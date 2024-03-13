package org.folio.circulation.domain.notice.schedule;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.notice.NoticeTiming.BEFORE;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;

public class LoanScheduledNoticeHandler extends ScheduledNoticeHandler {
  private final LoanPolicyRepository loanPolicyRepository;
  private final ZonedDateTime systemTime;

  public LoanScheduledNoticeHandler(Clients clients, LoanRepository loanRepository) {

    super(clients, loanRepository);
    this.systemTime = ClockUtil.getZonedDateTime();
    this.loanPolicyRepository = new LoanPolicyRepository(clients);
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(
    ScheduledNoticeContext context) {

    return ofAsync(() -> context)
      .thenApply(r -> r.next(this::failWhenNoticeHasNoLoanId))
      .thenCompose(r -> r.after(this::fetchTemplate))
      .thenCompose(r -> r.after(this::fetchLoan))
      .thenCompose(r -> r.after(this::fetchLostItemFeesForAgedToLostNotice))
      .thenCompose(r -> r.after(this::fetchPatronNoticePolicyIdForLoan));
  }

  protected Result<ScheduledNoticeContext> failWhenNoticeHasNoLoanId(ScheduledNoticeContext context) {
    String loanId = context.getNotice().getLoanId();

    return isEmpty(loanId)
      ? failed(new RecordNotFoundFailure("loan", loanId))
      : succeeded(context);
  }

  @Override
  protected boolean isNoticeIrrelevant(ScheduledNoticeContext context) {
    TriggeringEvent triggeringEvent = context.getNotice().getTriggeringEvent();

    switch (triggeringEvent) {
    case DUE_DATE:
      return dueDateNoticeIsNotRelevant(context);
    case AGED_TO_LOST:
      return agedToLostNoticeIsNotRelevant(context);
    default:
      var errorMessage = String.format("Unexpected triggering event %s",
        triggeringEvent.getRepresentation());
      log.error(errorMessage);
      throw new IllegalArgumentException(errorMessage);
    }
  }

  @Override
  protected CompletableFuture<Result<ScheduledNotice>> updateNotice(
    ScheduledNoticeContext context) {

    Loan loan = context.getLoan();
    ScheduledNotice notice = context.getNotice();
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    if (!noticeConfig.isRecurring() || isNoticeIrrelevant(context)) {
      return deleteNoticeAsIrrelevant(notice);
    }

    ZonedDateTime recurringNoticeNextRunTime = noticeConfig
      .getRecurringPeriod().plusDate(notice.getNextRunTime());

    if (isBeforeMillis(recurringNoticeNextRunTime, systemTime)) {
      recurringNoticeNextRunTime = noticeConfig.getRecurringPeriod().plusDate(systemTime);
    }

    ScheduledNotice nextRecurringNotice = notice.withNextRunTime(recurringNoticeNextRunTime);

    if (nextRecurringNoticeIsNotRelevant(nextRecurringNotice, loan)) {
      return deleteNoticeAsIrrelevant(notice);
    }

    return scheduledNoticesRepository.update(nextRecurringNotice);
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchLoan(
    ScheduledNoticeContext context) {

    // Also fetches user, item and item-related records (holdings, instance, location, etc.)
    return loanRepository.getById(context.getNotice().getLoanId())
      .thenCompose(r -> r.after(loanRepository::fetchLatestPatronInfoAddedComment))
      .thenCompose(r -> r.after(loanPolicyRepository::findPolicyForLoan))
      .thenApply(mapResult(context::withLoan))
      .thenApply(r -> r.next(this::failWhenLoanIsIncomplete));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchLostItemFeesForAgedToLostNotice(
    ScheduledNoticeContext context) {

    ScheduledNotice notice = context.getNotice();

    if (AGED_TO_LOST != notice.getTriggeringEvent()) {
      return ofAsync(() -> context);
    }

    Result<CqlQuery> query = exactMatchAny("feeFineType", lostItemFeeTypes());

    return accountRepository.findAccountsForLoanByQuery(context.getLoan(), query)
      .thenApply(r -> r.map(CollectionUtils::isNotEmpty))
      .thenApply(mapResult(context::withLostItemFeesForAgedToLostNoticeExist));
  }

  protected boolean dueDateNoticeIsNotRelevant(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    ZonedDateTime dueDate = loan.getDueDate();
    String loanId = loan.getId();

    ScheduledNotice notice = context.getNotice();
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    List<String> logMessages = new ArrayList<>();

    if (loan.isClosed()) {
      logMessages.add("Loan is closed");
    }
    if (noticeConfig.hasBeforeTiming() && isBeforeMillis(dueDate, systemTime)) {
      logMessages.add("Loan is overdue");
    }
    if (loan.hasItemWithAnyStatus(DECLARED_LOST, ItemStatus.AGED_TO_LOST, CLAIMED_RETURNED)) {
      logMessages.add(String.format("Recurring overdue notice for item in status \"%s\"",
        loan.getItemStatus()));
    }

    if (logMessages.isEmpty()) {
      return false;
    }

    log.warn("Due Date notice {} for loan {} is irrelevant: {}", notice.getId(), loanId, logMessages);
    return true;
  }

  private boolean agedToLostNoticeIsNotRelevant(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    ScheduledNotice notice = context.getNotice();
    List<String> logMessages = new ArrayList<>();

    if (loan.hasItemWithAnyStatus(DECLARED_LOST, CLAIMED_RETURNED)) {
      logMessages.add(
        String.format("Recurring notice for item in status \"%s\"", loan.getItemStatus()));
    }
    if (loan.isRenewed()) {
      logMessages.add("Loan was renewed");
    }
    if (loan.isClosed()) {
      logMessages.add("Loan is closed");
    }
    if (context.isLostItemFeesForAgedToLostNoticeExist()) {
      logMessages.add("Loan was charged Lost Item Fee(s)");
    }

    if (logMessages.isEmpty()) {
      return false;
    }

    log.warn("Aged To Lost notice {} for loan {} is irrelevant: {}",
      notice.getId(), loan.getId(), logMessages);

    return true;
  }

  private static boolean nextRecurringNoticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return noticeConfig.isRecurring() &&
      noticeConfig.getTiming() == BEFORE &&
      isAfterMillis(notice.getNextRunTime(), loan.getDueDate());
  }

  @Override
  protected NoticeLogContext buildNoticeLogContext(ScheduledNoticeContext context) {
    return new NoticeLogContext()
      .withUser(context.getLoan().getUser())
      .withItems(singletonList(buildNoticeLogContextItem(context)));
  }

  @Override
  protected JsonObject buildNoticeContextJson(ScheduledNoticeContext context) {
    return createLoanNoticeContext(context.getLoan());
  }

  @Override
  protected NoticeLogContextItem buildNoticeLogContextItem(ScheduledNoticeContext context) {
    return NoticeLogContextItem.from(context.getLoan())
      .withTemplateId(context.getNotice().getConfiguration().getTemplateId())
      .withTriggeringEvent(context.getNotice().getTriggeringEvent().getRepresentation())
      .withNoticePolicyId(context.getPatronNoticePolicyId());
  }

}
