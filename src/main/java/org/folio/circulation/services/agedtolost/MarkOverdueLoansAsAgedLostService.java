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
import static org.folio.circulation.support.http.client.PageLimit.limit;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;

public class MarkOverdueLoansAsAgedLostService {
  private static final int DEFAULT_MAXIMUM_LOANS_TO_PROCESS = 1000;

  private final LoanRepository loanRepository;
  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final ItemRepository itemRepository;
  private final StoreLoanAndItem storeLoanAndItem;
  private final PageLimit maximumNumberOfLoansToProcess;

  public MarkOverdueLoansAsAgedLostService(Clients clients, int maximumNumberOfLoansToProcess) {
    this.loanRepository = new LoanRepository(clients);
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.itemRepository = noLocationMaterialTypeAndLoanTypeInstance(clients);
    this.storeLoanAndItem = new StoreLoanAndItem(clients);
    this.maximumNumberOfLoansToProcess = limit(maximumNumberOfLoansToProcess);
  }

  public MarkOverdueLoansAsAgedLostService(Clients clients) {
    this(clients, DEFAULT_MAXIMUM_LOANS_TO_PROCESS);
  }

  public CompletableFuture<Result<Void>> processAgeToLost() {
    return fetchOverdueLoans()
      .thenCompose(r -> r.after(lostItemPolicyRepository::findLostItemPoliciesForLoans))
      .thenApply(this::getLoansThatHaveToBeAgedToLost)
      .thenCompose(loansResult -> itemRepository.fetchItemsFor(loansResult, Loan::withItem))
      .thenApply(this::markLoansAsAgedToLost)
      .thenCompose(this::updateLoansAndItemsInStorage);
  }

  private Result<MultipleRecords<Loan>> markLoansAsAgedToLost(
    Result<MultipleRecords<Loan>> loanRecordsResult) {

    return loanRecordsResult.map(loanRecords -> loanRecords.mapRecords(this::ageItemToLost));
  }

  private Loan ageItemToLost(Loan loan) {
    final LostItemPolicy lostItemPolicy = loan.getLostItemPolicy();
    final DateTime loanDueDate = loan.getDueDate();
    final DateTime whenToBill = lostItemPolicy
      .calculateDateTimeWhenPatronBilledForAgedToLost(loanDueDate);

    loan.setAgedToLostDelayedBilling(false, whenToBill);
    return loan.ageOverdueItemToLost();
  }

  private CompletableFuture<Result<Void>> updateLoansAndItemsInStorage(
    Result<MultipleRecords<Loan>> loanRecordsResult) {

    return loanRecordsResult
      .map(MultipleRecords::getRecords)
      .after(loans -> allOf(loans, storeLoanAndItem::updateLoanAndItemInStorage))
      .thenApply(r -> r.map(notUsed -> null));
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> fetchOverdueLoans() {
    return loanFetchQuery().after(
      query -> loanRepository.findByQuery(query, maximumNumberOfLoansToProcess));
  }

  private Result<MultipleRecords<Loan>> getLoansThatHaveToBeAgedToLost(
    Result<MultipleRecords<Loan>> loans) {

    return loans.map(multipleRecords ->
      multipleRecords.filter(this::shouldAgeLoanToLost));
  }

  private boolean shouldAgeLoanToLost(Loan loan) {
    return loan.getLostItemPolicy().canAgeLoanToLost(loan.getDueDate());
  }

  private Result<CqlQuery> loanFetchQuery() {
    final Result<CqlQuery> statusQuery = exactMatch("status.name", "Open");
    final Result<CqlQuery> dueDateQuery = lessThan("dueDate", getClockManager().getDateTime());
    final Result<CqlQuery> claimedReturnedQuery = notEqual("itemStatus", CLAIMED_RETURNED.getValue());
    final Result<CqlQuery> agedToLostQuery = notEqual("itemStatus", AGED_TO_LOST.getValue());

    return Result.combine(statusQuery, dueDateQuery, CqlQuery::and)
      .combine(claimedReturnedQuery, CqlQuery::and)
      .combine(agedToLostQuery, CqlQuery::and)
      .map(query -> query.sortBy(ascending("dueDate")));
  }
}
