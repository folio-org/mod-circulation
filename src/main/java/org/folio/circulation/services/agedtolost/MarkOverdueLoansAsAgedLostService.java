package org.folio.circulation.services.agedtolost;

import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.infrastructure.storage.inventory.ItemRepository.noLocationMaterialTypeAndLoanTypeInstance;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.lessThan;
import static org.folio.circulation.support.http.client.CqlQuery.notEqual;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.fetching.PageableFetcher;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.slf4j.Logger;

public class MarkOverdueLoansAsAgedLostService {
  private static final Logger log = getLogger(MarkOverdueLoansAsAgedLostService.class);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final ItemRepository itemRepository;
  private final StoreLoanAndItem storeLoanAndItem;
  private final EventPublisher eventPublisher;
  private final PageableFetcher<Loan> loanPageableFetcher;

  public MarkOverdueLoansAsAgedLostService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.itemRepository = noLocationMaterialTypeAndLoanTypeInstance(clients);
    this.storeLoanAndItem = new StoreLoanAndItem(clients);
    this.eventPublisher = new EventPublisher(clients.pubSubPublishingService());
    this.loanPageableFetcher = new PageableFetcher<>(new LoanRepository(clients));
  }

  public CompletableFuture<Result<Void>> processAgeToLost() {
    log.info("Running mark overdue loans as aged to lost process...");

    return loanPageableFetcher.processPages(loanFetchQuery(), this::processAgeToLost);
  }

  public CompletableFuture<Result<Void>> processAgeToLost(MultipleRecords<Loan> loans) {
    if (loans.isEmpty()) {
      log.info("No overdue loans to age to lost found");
      return ofAsync(() -> null);
    }

    return lostItemPolicyRepository.findLostItemPoliciesForLoans(loans)
      .thenApply(this::getLoansThatHaveToBeAgedToLost)
      .thenCompose(loansResult -> itemRepository.fetchItemsFor(loansResult, Loan::withItem))
      .thenApply(this::markLoansAsAgedToLost)
      .thenCompose(this::updateLoansAndItemsInStorage)
      .thenCompose(this::publishAgedToLostEvent);
  }

  private CompletableFuture<Result<Void>> publishAgedToLostEvent(Result<List<Loan>> allLoansResult) {
    return allLoansResult.after(allLoans -> allOf(allLoans, eventPublisher::publishAgedToLostEvent))
      .thenApply(r -> r.map(results -> null));
  }

  private Result<MultipleRecords<Loan>> markLoansAsAgedToLost(
    Result<MultipleRecords<Loan>> loanRecordsResult) {

    return loanRecordsResult.map(loanRecords -> loanRecords.mapRecords(this::ageItemToLost));
  }

  private Loan ageItemToLost(Loan loan) {
    final LostItemPolicy lostItemPolicy = loan.getLostItemPolicy();
    final DateTime ageToLostDate = getClockManager().getDateTime();

    final DateTime whenToBill = lostItemPolicy
      .calculateDateTimeWhenPatronBilledForAgedToLost(ageToLostDate);

    loan.setAgedToLostDelayedBilling(false, whenToBill);
    return loan.ageOverdueItemToLost(ageToLostDate);
  }

  private CompletableFuture<Result<List<Loan>>> updateLoansAndItemsInStorage(
    Result<MultipleRecords<Loan>> loanRecordsResult) {

    return loanRecordsResult
      .map(MultipleRecords::getRecords)
      .after(loans -> allOf(loans, storeLoanAndItem::updateLoanAndItemInStorage));
  }

  private Result<MultipleRecords<Loan>> getLoansThatHaveToBeAgedToLost(
    Result<MultipleRecords<Loan>> loansResult) {

    return loansResult.map(loans -> {
      final var loansToAgeToLost = loans.filter(this::shouldAgeLoanToLost);
      log.info("{} out of {} loans is going to be aged to lost", loansToAgeToLost.size(),
        loans.size());

      return loansToAgeToLost;
    });
  }

  private boolean shouldAgeLoanToLost(Loan loan) {
    return loan.getLostItemPolicy().canAgeLoanToLost(loan.getDueDate());
  }

  private CqlQuery loanFetchQuery() {
    final Result<CqlQuery> statusQuery = exactMatch("status.name", "Open");
    final Result<CqlQuery> dueDateQuery = lessThan("dueDate", getClockManager().getDateTime());
    final Result<CqlQuery> claimedReturnedQuery = notEqual("itemStatus", CLAIMED_RETURNED.getValue());
    final Result<CqlQuery> agedToLostQuery = notEqual("itemStatus", AGED_TO_LOST.getValue());

    return statusQuery.combine(dueDateQuery, CqlQuery::and)
      .combine(claimedReturnedQuery, CqlQuery::and)
      .combine(agedToLostQuery, CqlQuery::and)
      .map(query -> query.sortBy(ascending("dueDate")))
      .value();
  }
}
