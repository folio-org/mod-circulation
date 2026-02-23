package org.folio.circulation.domain.notice.schedule;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.Collections.singletonList;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createFeeFineChargeAndActionNoticeContext;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createFeeFineChargeNoticeContext;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class FeeFineScheduledNoticeHandler extends ScheduledNoticeHandler {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final FeeFineActionRepository actionRepository;
  private final LoanPolicyRepository loanPolicyRepository;

  public FeeFineScheduledNoticeHandler(Clients clients, LoanRepository loanRepository) {
    super(clients, loanRepository);
    this.actionRepository = new FeeFineActionRepository(clients);
    this.loanPolicyRepository = new LoanPolicyRepository(clients);
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(
    ScheduledNoticeContext context) {

    log.debug("fetchData:: fetching data for scheduled notice {}", context.getNotice().getId());

    return ofAsync(() -> context)
      .thenCompose(r -> r.after(this::fetchTemplate))
      .thenCompose(r -> r.after(this::fetchAction))
      .thenCompose(r -> r.after(this::fetchAccount))
      .thenCompose(r -> r.after(this::fetchChargeAction))
      .thenCompose(r -> r.after(this::fetchLoan))
      .thenCompose(r -> r.after(this::fetchPatronNoticePolicyIdForLoan));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchAction(
    ScheduledNoticeContext context) {

    log.debug("fetchAction:: fetching fee/fine action {}", context.getNotice().getFeeFineActionId());

    return actionRepository.findById(context.getNotice().getFeeFineActionId())
      .thenApply(mapResult(context::withCurrentAction));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchAccount(
    ScheduledNoticeContext context) {

    log.debug("fetchAccount:: fetching account for action");

    return accountRepository.findAccountForAction(context.getCurrentAction())
      .thenApply(mapResult(context::withAccount));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchChargeAction(
    ScheduledNoticeContext context) {

    log.debug("fetchChargeAction:: fetching charge action for account {}", context.getAccount().getId());

    return actionRepository.findChargeActionForAccount(context.getAccount())
      .thenApply(mapResult(context::withChargeAction));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchLoan(
    ScheduledNoticeContext context) {

    log.debug("fetchLoan:: fetching loan for account {}", context.getAccount().getId());

    if (isNoticeIrrelevant(context)) {
      return ofAsync(() -> context);
    }

    // this also fetches user and item
    return loanRepository.findLoanForAccount(context.getAccount())
      .thenCompose(r -> r.after(loanRepository::fetchLatestPatronInfoAddedComment))
      .thenCompose(r -> r.after(loanPolicyRepository::findPolicyForLoan))
      .thenApply(mapResult(context::withLoan))
      .thenApply(r -> r.next(this::failWhenLoanIsIncomplete));
  }

  @Override
  protected CompletableFuture<Result<ScheduledNotice>> updateNotice(ScheduledNoticeContext context) {
    log.debug("updateNotice:: updating scheduled notice {}", context.getNotice().getId());
    ScheduledNotice notice = context.getNotice();

    return isNoticeIrrelevant(context) || !notice.getConfiguration().isRecurring()
      ? deleteNoticeAsIrrelevant(notice)
      : scheduledNoticesRepository.update(getNextRecurringNotice(notice));
  }

  @Override
  protected boolean isNoticeIrrelevant(ScheduledNoticeContext context) {
    log.debug("isNoticeIrrelevant:: checking if notice is irrelevant");
    return context.getAccount().isClosed() &&
      !context.getNotice().getTriggeringEvent().isAutomaticFeeFineAdjustment();
  }

  @Override
  protected NoticeLogContext buildNoticeLogContext(ScheduledNoticeContext context) {
    log.debug("buildNoticeLogContext:: building notice log context");
    return new NoticeLogContext()
      .withUser(context.getLoan().getUser())
      .withAccountId(context.getAccount().getId())
      .withItems(singletonList(buildNoticeLogContextItem(context)));
  }

  @Override
  protected NoticeLogContextItem buildNoticeLogContextItem(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    ScheduledNotice notice = context.getNotice();

    return NoticeLogContextItem.from(loan)
      .withTemplateId(notice.getConfiguration().getTemplateId())
      .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation())
      .withNoticePolicyId(context.getPatronNoticePolicyId());
  }

  @Override
  protected JsonObject buildNoticeContextJson(ScheduledNoticeContext context) {
    log.debug("buildNoticeContextJson:: building notice context JSON");
    return context.getNotice().getTriggeringEvent().isAutomaticFeeFineAdjustment()
      ? createFeeFineChargeAndActionNoticeContext(context.getAccount(),
      context.getLoan(), context.getCurrentAction(), context.getChargeAction())
      : createFeeFineChargeNoticeContext(context.getAccount(), context.getLoan(),
      context.getChargeAction());
  }

  private static ScheduledNotice getNextRecurringNotice(ScheduledNotice notice) {
    Period recurringPeriod = notice.getConfiguration().getRecurringPeriod();
    ZonedDateTime nextRunTime = recurringPeriod.plusDate(notice.getNextRunTime());
    ZonedDateTime now = getZonedDateTime();

    if (isBeforeMillis(nextRunTime, now)) {
      nextRunTime = recurringPeriod.plusDate(now);
    }

    return notice.withNextRunTime(nextRunTime);
  }

}
