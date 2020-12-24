package org.folio.circulation.domain.notice.schedule;

import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.domain.notice.NoticeTiming.BEFORE;
import static org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeHandler.RecordType.ITEM;
import static org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeHandler.RecordType.LOAN;
import static org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeHandler.RecordType.TEMPLATE;
import static org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeHandler.RecordType.USER;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.DUE_DATE;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections.CollectionUtils;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class LoanScheduledNoticeHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String ERROR_MESSAGE_TEMPLATE = "Sending scheduled notice {} failed: {}";

  public static LoanScheduledNoticeHandler using(Clients clients, DateTime systemTime) {
    return new LoanScheduledNoticeHandler(
      new LoanRepository(clients),
      new LoanPolicyRepository(clients),
      new PatronNoticePolicyRepository(clients),
      PatronNoticeService.using(clients),
      ScheduledNoticesRepository.using(clients),
      clients.templateNoticeClient(),
      new AccountRepository(clients),
      systemTime);
  }

  private final LoanRepository loanRepository;
  private final LoanPolicyRepository loanPolicyRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;
  private final PatronNoticeService patronNoticeService;
  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final CollectionResourceClient templateNoticesClient;
  private final AccountRepository accountRepository;
  private final DateTime systemTime;

  public CompletableFuture<Result<Collection<ScheduledNotice>>> handleNotices(
    Collection<ScheduledNotice> scheduledNotices) {

    CompletableFuture<Result<ScheduledNotice>> future = completedFuture(succeeded(null));
    for (ScheduledNotice scheduledNotice : scheduledNotices) {
      future = future.thenCompose(r -> handleNotice(scheduledNotice));
    }
    return future.thenApply(r -> succeeded(scheduledNotices));
  }

  private CompletableFuture<Result<ScheduledNotice>> handleNotice(ScheduledNotice notice) {
    return collectRequiredData(notice)
      .thenCompose(r -> r.afterWhen(
        records -> isNoticeIrrelevant(notice, records),
        records -> handleIrrelevantNotice(notice),
        records -> handleRelevantNotice(notice, records)));
  }

  CompletableFuture<Result<LoanAndRelatedRecords>> collectRequiredData(ScheduledNotice notice) {
    return failIfNoticeHasNoLoanId(notice)
      .after(this::failIfTemplateDoesNotExist)
      .thenCompose(r -> r.after(this::fetchLoanAndRelatedRecords))
      .thenApply(r -> r.next(this::failIfLoanRelatedRecordWasNotFound))
      .thenCompose(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenCompose(r -> handleDataCollectionResult(r, notice));
  }

  private Result<ScheduledNotice> failIfNoticeHasNoLoanId(ScheduledNotice notice) {
    return notice.getLoanId() == null
      ? buildRecordNotFoundFailure(LOAN, null)
      : succeeded(notice);
  }

  private CompletableFuture<Result<ScheduledNotice>> failIfTemplateDoesNotExist(
    ScheduledNotice notice) {

    final String templateId = notice.getConfiguration().getTemplateId();

    return templateNoticesClient.get(templateId)
      .thenApply(r -> r.next(response -> response.getStatusCode() == SC_NOT_FOUND
        ? buildRecordNotFoundFailure(TEMPLATE, templateId)
        : succeeded(notice)));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> fetchLoanAndRelatedRecords(
    ScheduledNotice notice) {

    // Returns RecordNotFoundFailure if loan was not found
    // Also fetches user, item and item-related records (holdings, instance, location, etc.)
    return loanRepository.getById(notice.getLoanId())
      .thenApply(r -> r.map(LoanAndRelatedRecords::new));
  }

  private Result<LoanAndRelatedRecords> failIfLoanRelatedRecordWasNotFound(
    LoanAndRelatedRecords records) {

    final Loan loan = records.getLoan();

    if (loan.getItem().isNotFound()) {
      return buildRecordNotFoundFailure(ITEM, loan.getItemId());
    }

    if (loan.getUser() == null) {
      return buildRecordNotFoundFailure(USER, loan.getUserId());
    }

    return succeeded(records);
  }

  private CompletableFuture<Result<ScheduledNotice>> handleIrrelevantNotice(ScheduledNotice notice) {
    log.info("Deleting scheduled notice {} as irrelevant", notice.getId());
    return scheduledNoticesRepository.delete(notice);
  }

  private CompletableFuture<Result<ScheduledNotice>> handleRelevantNotice(ScheduledNotice notice,
    LoanAndRelatedRecords records) {

    return sendNotice(records, notice)
      .thenCompose(r -> r.after(ignored -> updateNotice(records, notice)))
      .whenComplete((result, throwable) -> {
        if (throwable != null) {
          log.error(ERROR_MESSAGE_TEMPLATE, notice.getId(), throwable.getMessage());
        } else if (result.failed()) {
          log.error(ERROR_MESSAGE_TEMPLATE, notice.getId(), result.cause());
        }
      });
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> handleDataCollectionResult(
    Result<LoanAndRelatedRecords> result, ScheduledNotice notice) {

    if (result.failed()) {
      if (failedToFindRequiredRecord(result)) {
        log.warn("Deleting scheduled notice {} without sending: {}", notice.getId(), result.cause());
        return scheduledNoticesRepository.delete(notice)
          .thenApply(r -> r.next(ignored -> result));
      }

      log.error("Data collection for scheduled notice {} failed: {}", notice.getId(), result.cause());
    }

    return completedFuture(result);
  }

  private CompletableFuture<Result<Boolean>> isNoticeIrrelevant(ScheduledNotice notice,
    LoanAndRelatedRecords relatedRecords) {

    final Loan loan = relatedRecords.getLoan();

    if (noticeIsNotRelevant(notice, loan)) {
      return ofAsync(() -> true);
    }

    // should stop sending "Aged to lost" notices once a lost item fee is charged
    if (notice.getTriggeringEvent() == AGED_TO_LOST) {
      Result<CqlQuery> query = exactMatchAny("feeFineType", lostItemFeeTypes());
      return accountRepository.findAccountsForLoanByQuery(loan, query)
        .thenApply(r -> r.map(CollectionUtils::isNotEmpty))
        .whenComplete((result, throwable) -> {
          if (result != null && result.succeeded() && isTrue(result.value())) {
            log.info("Lost item fee(s) for loan {} exist. Scheduled \"Aged to lost\" " +
              "notice {} is no longer relevant.", loan.getId(), notice.getId());
          }
        });
    }

    return ofAsync(() -> false);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> sendNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNotice notice) {

    final Loan loan = relatedRecords.getLoan();

    JsonObject loanNoticeContext = TemplateContextUtil.createLoanNoticeContext(loan);

    NoticeLogContextItem logContextItem = NoticeLogContextItem.from(loan)
      .withTemplateId(notice.getConfiguration().getTemplateId())
      .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation());

    return noticePolicyRepository.lookupPolicyId(loan.getItem(), loan.getUser())
      .thenCompose(r -> r.after(policy -> patronNoticeService.acceptScheduledNoticeEvent(
        notice.getConfiguration(), relatedRecords.getUserId(), loanNoticeContext,
        new NoticeLogContext().withUser(loan.getUser())
          .withItems(singletonList(logContextItem.withNoticePolicyId(policy.getPolicyId()))))))
      .thenApply(r -> r.map(v -> relatedRecords));
  }

  public CompletableFuture<Result<ScheduledNotice>> updateNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNotice notice) {

    Loan loan = relatedRecords.getLoan();
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    if (!noticeConfig.isRecurring() || noticeIsNotRelevant(notice, loan)) {
      return scheduledNoticesRepository.delete(notice);
    }

    DateTime recurringNoticeNextRunTime = notice.getNextRunTime()
      .plus(noticeConfig.getRecurringPeriod().timePeriod());

    if (recurringNoticeNextRunTime.isBefore(systemTime)) {
      recurringNoticeNextRunTime =
        systemTime.plus(noticeConfig.getRecurringPeriod().timePeriod());
    }

    ScheduledNotice nextRecurringNotice = notice.withNextRunTime(recurringNoticeNextRunTime);

    if (nextRecurringNoticeIsNotRelevant(nextRecurringNotice, loan)) {
      return scheduledNoticesRepository.delete(notice);
    }

    return scheduledNoticesRepository.update(nextRecurringNotice);
  }

  public boolean noticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    TriggeringEvent triggeringEvent = notice.getTriggeringEvent();
    switch (triggeringEvent) {
      case DUE_DATE:
        return dueDateNoticeIsNotRelevant(notice, loan);
      case AGED_TO_LOST:
        return agedToLostNoticeIsNotRelevant(loan);
      default:
        var errorMessage = String.format("Unexpected triggering event %s",
          triggeringEvent.getRepresentation());
        log.error(errorMessage);
        throw new IllegalArgumentException(errorMessage);
    }
  }

  private boolean dueDateNoticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    if (beforeDueDateNoticeIsNotRelevant(notice, loan)) {
      log.warn("The notice {} is irrelevant. The due date is before", notice.getId());
      return true;
    }
    if (loan.isDeclaredLost()) {
      log.warn("The notice {} is irrelevant. The item {} was declared lost",
        notice.getId(), loan.getItemId());
      return true;
    }
    if (loan.getItem().getStatus() == ItemStatus.AGED_TO_LOST) {
      log.warn("The notice {} is irrelevant. The item {} was aged to lost",
        notice.getId(), loan.getItemId());
      return true;
    }
    if (loan.isRenewed()) {
      log.warn("The notice {} is irrelevant. The item {} was renewed",
        notice.getId(), loan.getItemId());
      return true;
    }
    if (loan.getItem().isClaimedReturned()) {
      log.warn("The notice {} is irrelevant. The item {} was claimed returned",
        notice.getId(), loan.getItemId());
      return true;
    }
    if (loan.hasDueDateChanged() && loan.getDueDate().isAfter(systemTime)) {
      log.warn("The notice {} is irrelevant. The due date for the loan {} was changed",
        notice.getId(), loan.getId());
      return true;
    }
    if (loan.isClosed()) {
      log.warn("The notice {} is irrelevant. The loan {} is closed", notice.getId(), loan.getId());
      return true;
    }
    return false;
  }

  private boolean agedToLostNoticeIsNotRelevant(Loan loan) {
    if (loan.isDeclaredLost()) {
      log.warn("Aged to lost notice is irrelevant. The item {} was declared lost",
        loan.getItemId());
      return true;
    }
    if (loan.getItem().isClaimedReturned()) {
      log.warn("Aged to lost notice is irrelevant. The item {} was claimed returned",
        loan.getItemId());
      return true;
    }
    if (loan.isClosed()) {
      log.warn("Aged to lost notice is irrelevant. The loan {} is closed", loan.getId());
      return true;
    }
    return false;
  }

  private boolean beforeDueDateNoticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return notice.getTriggeringEvent() == DUE_DATE
      && noticeConfig.getTiming() == BEFORE
      && loan.getDueDate().isBefore(systemTime);
  }

  private boolean nextRecurringNoticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return noticeConfig.isRecurring() &&
      noticeConfig.getTiming() == BEFORE &&
      notice.getNextRunTime().isAfter(loan.getDueDate());
  }

  static <T> boolean failedToFindRequiredRecord(Result<T> result) {
    return result.failed()
      && result.cause() instanceof RecordNotFoundFailure
      && Arrays.stream(RecordType.values())
      .map(RecordType::getValue)
      .anyMatch(t -> t.equals(((RecordNotFoundFailure) result.cause()).getRecordType()));
  }

  private static <T> Result<T> buildRecordNotFoundFailure(RecordType recordType, String recordId) {
    return failed(new RecordNotFoundFailure(recordType.getValue(), recordId));
  }

  @AllArgsConstructor
  @Getter(AccessLevel.PRIVATE)
  enum RecordType {
    USER("user"),
    ITEM("item"),
    LOAN("loan"),
    TEMPLATE("template");

    private final String value;
  }

}
