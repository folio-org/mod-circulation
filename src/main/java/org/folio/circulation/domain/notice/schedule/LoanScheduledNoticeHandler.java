package org.folio.circulation.domain.notice.schedule;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.domain.notice.NoticeTiming.BEFORE;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.DUE_DATE;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections.CollectionUtils;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class LoanScheduledNoticeHandler extends ScheduledNoticeHandler {
  private final LoanPolicyRepository loanPolicyRepository;
  private final DateTime systemTime;

  public LoanScheduledNoticeHandler(Clients clients, DateTime systemTime) {
    super(clients);
    this.systemTime = systemTime;
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
      .thenCompose(r -> r.after(this::fetchPatronNoticePolicyId));
  }

  private Result<ScheduledNoticeContext> failWhenNoticeHasNoLoanId(ScheduledNoticeContext context) {
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

    DateTime recurringNoticeNextRunTime = notice.getNextRunTime()
      .plus(noticeConfig.getRecurringPeriod().timePeriod());

    if (recurringNoticeNextRunTime.isBefore(systemTime)) {
      recurringNoticeNextRunTime =
        systemTime.plus(noticeConfig.getRecurringPeriod().timePeriod());
    }

    ScheduledNotice nextRecurringNotice = notice.withNextRunTime(recurringNoticeNextRunTime);

    if (nextRecurringNoticeIsNotRelevant(nextRecurringNotice, loan)) {
      return deleteNoticeAsIrrelevant(notice);
    }

    return scheduledNoticesRepository.update(nextRecurringNotice);
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchLoan(
    ScheduledNoticeContext context) {

    // Also fetches user, item and item-related records (holdings, instance, location, etc.)
    return loanRepository.getById(context.getNotice().getLoanId())
      .thenCompose(r -> r.after(loanPolicyRepository::findPolicyForLoan))
      .thenApply(mapResult(context::withLoan))
      .thenApply(this::failWhenReferencedEntityWasNotFound);
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

  private boolean dueDateNoticeIsNotRelevant(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    ScheduledNotice notice = context.getNotice();
    List<String> logMessages = new ArrayList<>();

    if (beforeDueDateNoticeIsNotRelevant(notice, loan)) {
      logMessages.add(String.format("Due date of loan %s is before now", loan.getId()));
    }
    if (loan.isDeclaredLost()) {
      logMessages.add(String.format("Item %s was declared lost", loan.getItemId()));
    }
    if (loan.getItem().getStatus() == ItemStatus.AGED_TO_LOST) {
      logMessages.add(String.format("Item %s was aged to lost", loan.getItemId()));
    }
    if (loan.isRenewed()) {
      logMessages.add(String.format("Item %s was renewed", loan.getItemId()));
    }
    if (loan.getItem().isClaimedReturned()) {
      logMessages.add(String.format("Item %s was claimed returned", loan.getItemId()));
    }
    if (loan.hasDueDateChanged() && loan.getDueDate().isAfter(systemTime)) {
      logMessages.add(String.format("Due date for the loan %s was changed", loan.getId()));
    }
    if (loan.isClosed()) {
      logMessages.add(String.format("Loan %s is closed", loan.getId()));
    }
    if (!logMessages.isEmpty()) {
      log.warn("Due Date notice {} is irrelevant: {}", notice.getId(), logMessages);
      return true;
    }

    return false;
  }

  private boolean agedToLostNoticeIsNotRelevant(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    List<String> logMessages = new ArrayList<>();

    if (loan.isDeclaredLost()) {
      logMessages.add(String.format("Item %s was declared lost", loan.getItemId()));
    }
    if (loan.getItem().isClaimedReturned()) {
      logMessages.add(String.format("Item %s was claimed returned", loan.getItemId()));
    }
    if (loan.isRenewed()) {
      logMessages.add(String.format("Item %s was renewed", loan.getItemId()));
    }
    if (loan.isClosed()) {
      logMessages.add(String.format("Loan %s is closed", loan.getId()));
    }
    if (context.isLostItemFeesForAgedToLostNoticeExist()) {
      logMessages.add(String.format("Loan %s was charged Lost Item Fee(s)", loan.getItemId()));
    }
    if (!logMessages.isEmpty()) {
      log.warn("Aged To Lost notice {} is irrelevant: {}", context.getNotice().getId(), logMessages);
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

  private static boolean nextRecurringNoticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return noticeConfig.isRecurring() &&
      noticeConfig.getTiming() == BEFORE &&
      notice.getNextRunTime().isAfter(loan.getDueDate());
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

  protected static NoticeLogContextItem buildNoticeLogContextItem(ScheduledNoticeContext context) {
    return NoticeLogContextItem.from(context.getLoan())
      .withTemplateId(context.getNotice().getConfiguration().getTemplateId())
      .withTriggeringEvent(context.getNotice().getTriggeringEvent().getRepresentation())
      .withNoticePolicyId(context.getPatronNoticePolicyId());
  }

}
