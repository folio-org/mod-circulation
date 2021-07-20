package org.folio.circulation.domain.notice.schedule;

import static java.util.Collections.singletonList;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createFeeFineNoticeContext;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.Period;

import io.vertx.core.json.JsonObject;

public class FeeFineScheduledNoticeHandler extends ScheduledNoticeHandler {
  private final FeeFineActionRepository actionRepository;

  public FeeFineScheduledNoticeHandler(Clients clients) {
    super(clients);
    this.actionRepository = new FeeFineActionRepository(clients);
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(ScheduledNoticeContext context) {
    return ofAsync(() -> context)
      .thenCompose(r -> r.after(this::fetchTemplate))
      .thenCompose(r -> r.after(this::fetchAction))
      .thenCompose(r -> r.after(this::fetchAccount))
      .thenCompose(r -> r.after(this::fetchLoan))
      .thenCompose(r -> r.after(this::fetchPatronNoticePolicyId));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchAction(
    ScheduledNoticeContext context) {

    return actionRepository.findById(context.getNotice().getFeeFineActionId())
      .thenApply(mapResult(context::withAction));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchAccount(
    ScheduledNoticeContext context) {

    return accountRepository.findAccountForAction(context.getAction())
      .thenApply(mapResult(context::withAccount));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchLoan(
    ScheduledNoticeContext context) {

    if (isNoticeIrrelevant(context)) {
      return ofAsync(() -> context);
    }

    // this also fetches user and item
    return loanRepository.findLoanForAccount(context.getAccount())
      .thenApply(mapResult(context::withLoan))
      .thenApply(this::failWhenReferencedEntityWasNotFound);
  }

  @Override
  protected CompletableFuture<Result<ScheduledNotice>> updateNotice(ScheduledNoticeContext context) {
    ScheduledNotice notice = context.getNotice();

    return isNoticeIrrelevant(context) || !notice.getConfiguration().isRecurring()
      ? deleteNoticeAsIrrelevant(notice)
      : scheduledNoticesRepository.update(getNextRecurringNotice(notice));
  }

  @Override
  protected boolean isNoticeIrrelevant(ScheduledNoticeContext context) {
    return context.getAccount().isClosed() &&
      !context.getNotice().getTriggeringEvent().isAutomaticFeeFineAdjustment();
  }

  @Override
  protected NoticeLogContext buildNoticeLogContext(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    ScheduledNotice notice = context.getNotice();

    NoticeLogContextItem logContextItem = NoticeLogContextItem.from(loan)
      .withTemplateId(notice.getConfiguration().getTemplateId())
      .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation())
      .withNoticePolicyId(context.getPatronNoticePolicyId());

    return new NoticeLogContext()
      .withUser(loan.getUser())
      .withAccountId(context.getAccount().getId())
      .withItems(singletonList(logContextItem));
  }

  @Override
  protected JsonObject buildNoticeContextJson(ScheduledNoticeContext context) {
    return context.getNotice().getTriggeringEvent().isAutomaticFeeFineAdjustment()
      ? createFeeFineNoticeContext(context.getAccount(), context.getLoan(), context.getAction())
      : createFeeFineNoticeContext(context.getAccount(), context.getLoan());
  }

  private static ScheduledNotice getNextRecurringNotice(ScheduledNotice notice) {
    Period recurringPeriod = notice.getConfiguration().getRecurringPeriod().timePeriod();
    DateTime nextRunTime = notice.getNextRunTime().plus(recurringPeriod);
    DateTime now = getClockManager().getDateTime();

    if (nextRunTime.isBefore(now)) {
      nextRunTime = now.plus(recurringPeriod);
    }

    return notice.withNextRunTime(nextRunTime);
  }

}
