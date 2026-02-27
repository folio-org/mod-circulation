package org.folio.circulation.services.agedtolost;

import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.lessThan;
import static org.folio.circulation.support.http.client.CqlQuery.notEqual;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.fetching.PageableFetcher;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

public class MarkOverdueLoansAsAgedLostService {
  private static final Logger log = LogManager.getLogger(MarkOverdueLoansAsAgedLostService.class);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final ItemRepository itemRepository;
  private final StoreLoanAndItem storeLoanAndItem;
  private final EventPublisher eventPublisher;
  private final PageableFetcher<Loan> loanPageableFetcher;
  private final LoanScheduledNoticeService loanScheduledNoticeService;
  private final UserRepository userRepository;

  public MarkOverdueLoansAsAgedLostService(Clients clients,
    ItemRepository itemRepository, LoanRepository loanRepository) {

    this.itemRepository = itemRepository;
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);
    this.eventPublisher = new EventPublisher(clients);
    this.loanPageableFetcher = new PageableFetcher<>(loanRepository);
    this.loanScheduledNoticeService = LoanScheduledNoticeService.using(clients);
    this.userRepository = new UserRepository(clients);
  }

  public CompletableFuture<Result<Void>> processAgeToLost() {
    log.info("processAgeToLost:: running mark overdue loans as aged to lost process...");

    return loanFetchQuery()
      .after(query -> loanPageableFetcher.processPages(query, this::processAgeToLost));
  }

  public CompletableFuture<Result<Void>> processAgeToLost(MultipleRecords<Loan> loans) {
    log.debug("processAgeToLost:: parameters loans count: {}", () -> multipleRecordsAsString(loans));
    if (loans.isEmpty()) {
      log.info("processAgeToLost:: no overdue loans to age to lost found");
      return ofAsync(() -> null);
    }

    return lostItemPolicyRepository.findLostItemPoliciesForLoans(loans)
      .thenApply(this::getLoansThatHaveToBeAgedToLost)
      .thenCompose(loansResult -> itemRepository.fetchItemsFor(loansResult, Loan::withItem))
      .thenApply(this::excludeLoansThatHaveNoItem)
      .thenApply(this::markLoansAsAgedToLost)
      .thenCompose(this::updateLoansAndItemsInStorage)
      .thenCompose(this::publishAgedToLostEvents)
      .thenCompose(this::scheduleAgedToLostNotices);
  }

  private CompletableFuture<Result<List<Loan>>> publishAgedToLostEvents(
    Result<List<Loan>> allLoansResult) {

    log.debug("publishAgedToLostEvents:: publishing aged to lost events");
    return allLoansResult.after(allLoans -> allOf(allLoans, eventPublisher::publishAgedToLostEvents))
      .thenApply(r -> r.next(ignored -> allLoansResult));
  }

  private Result<MultipleRecords<Loan>> markLoansAsAgedToLost(
    Result<MultipleRecords<Loan>> loanRecordsResult) {

    return loanRecordsResult.map(loanRecords -> loanRecords.mapRecords(this::ageItemToLost));
  }

  private Loan ageItemToLost(Loan loan) {
    final LostItemPolicy lostItemPolicy = loan.getLostItemPolicy();
    final ZonedDateTime ageToLostDate = ClockUtil.getZonedDateTime();
    final boolean isRecalled = loan.wasDueDateChangedByRecall();

    final ZonedDateTime whenToBill = lostItemPolicy
      .calculateDateTimeWhenPatronBilledForAgedToLost(isRecalled, ageToLostDate);

    log.info("ageItemToLost:: billing date for loan [{}] is [{}], is recalled [{}]", loan.getId(),
      whenToBill, isRecalled);

    loan.setAgedToLostDelayedBilling(false, whenToBill);
    return loan.ageOverdueItemToLost(ageToLostDate);
  }

  private CompletableFuture<Result<List<Loan>>> updateLoansAndItemsInStorage(
    Result<MultipleRecords<Loan>> loanRecordsResult) {

    log.debug("updateLoansAndItemsInStorage:: updating loans and items in storage");
    return loanRecordsResult
      .map(MultipleRecords::getRecords)
      .after(loans -> allOf(loans, storeLoanAndItem::updateLoanAndItemInStorage));
  }

  private Result<MultipleRecords<Loan>> getLoansThatHaveToBeAgedToLost(
    Result<MultipleRecords<Loan>> loansResult) {

    return loansResult.map(loans -> {
      final var loansToAgeToLost = loans.filter(this::shouldAgeLoanToLost);
      log.info("getLoansThatHaveToBeAgedToLost:: {} out of {} loans is going to be aged to lost",
        loansToAgeToLost.size(), loans.size());

      return loansToAgeToLost;
    });
  }

  private boolean shouldAgeLoanToLost(Loan loan) {
    final boolean isRecalled = loan.wasDueDateChangedByRecall();
    final boolean shouldAgeToLost = loan.getLostItemPolicy().canAgeLoanToLost(
      isRecalled, loan.getDueDate());

    log.info("shouldAgeLoanToLost:: loan [{}] - will be aged to lost - [{}], is recalled [{}]", loan.getId(),
      shouldAgeToLost, isRecalled);

    return shouldAgeToLost;
  }

  private Result<CqlQuery> loanFetchQuery() {
    log.debug("loanFetchQuery:: building loan fetch query");
    final Result<CqlQuery> statusQuery = exactMatch("status.name", "Open");
    final Result<CqlQuery> dueDateQuery = lessThan("dueDate", formatDateTime(ClockUtil.getZonedDateTime()));
    final Result<CqlQuery> claimedReturnedQuery = notEqual("itemStatus", CLAIMED_RETURNED.getValue());
    final Result<CqlQuery> agedToLostQuery = notEqual("itemStatus", AGED_TO_LOST.getValue());
    final Result<CqlQuery> declaredLostQuery = notEqual("itemStatus", DECLARED_LOST.getValue());

    return statusQuery.combine(dueDateQuery, CqlQuery::and)
      .combine(claimedReturnedQuery, CqlQuery::and)
      .combine(agedToLostQuery, CqlQuery::and)
      .combine(declaredLostQuery, CqlQuery::and)
      .map(query -> query.sortBy(ascending("dueDate")));
  }

  private CompletableFuture<Result<Void>> scheduleAgedToLostNotices(Result<List<Loan>> result) {
    log.debug("scheduleAgedToLostNotices:: scheduling aged to lost notices");
    return result.after(userRepository::findUsersForLoans)
      .thenApply(r -> r.next(loanScheduledNoticeService::scheduleAgedToLostNotices));
  }

  private Result<MultipleRecords<Loan>> excludeLoansThatHaveNoItem(
    Result<MultipleRecords<Loan>> recordsResult) {

    return recordsResult.map(records -> records
      .filter(loan -> {
        if (loan.getItem() == null || loan.getItem().isNotFound()) {
          log.warn("excludeLoansThatHaveNoItem:: no item [{}] exists for loan [{}]", loan.getItemId(), loan.getId());
        }

        return loan.getItem() != null && loan.getItem().isFound();
      }));
  }
}
