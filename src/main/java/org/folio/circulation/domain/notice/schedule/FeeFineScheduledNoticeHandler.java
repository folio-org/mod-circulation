package org.folio.circulation.domain.notice.schedule;

import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createFeeFineNoticeContext;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

public class FeeFineScheduledNoticeHandler {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final PatronNoticeService patronNoticeService;
  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final FeeFineActionRepository actionRepository;
  private final AccountRepository accountRepository;
  private final LoanRepository loanRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;

  private FeeFineScheduledNoticeHandler(PatronNoticeService patronNoticeService,
    ScheduledNoticesRepository scheduledNoticesRepository,
    FeeFineActionRepository actionRepository,
    AccountRepository accountRepository,
    LoanRepository loanRepository,
    PatronNoticePolicyRepository noticePolicyRepository) {

    this.patronNoticeService = patronNoticeService;
    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.actionRepository = actionRepository;
    this.accountRepository = accountRepository;
    this.loanRepository = loanRepository;
    this.noticePolicyRepository = noticePolicyRepository;
  }

  public static FeeFineScheduledNoticeHandler using(Clients clients) {
    return new FeeFineScheduledNoticeHandler(
      PatronNoticeService.using(clients),
      ScheduledNoticesRepository.using(clients),
      new FeeFineActionRepository(clients),
      new AccountRepository(clients),
      new LoanRepository(clients),
      new PatronNoticePolicyRepository(clients));
  }

  public CompletableFuture<Result<List<ScheduledNotice>>> handleNotices(
    Collection<ScheduledNotice> scheduledNotices) {

    return allOf(scheduledNotices, this::handleNotice);
  }

  public CompletableFuture<Result<ScheduledNotice>> handleNotice(ScheduledNotice notice) {
    return ofAsync(() -> new FeeFineNoticeContext(notice))
      .thenCompose(this::fetchAction)
      .thenCompose(this::fetchAccount)
      .thenCompose(this::fetchLoan)
      .thenCompose(r -> r.after(this::processNotice));
  }

  private CompletableFuture<Result<FeeFineNoticeContext>> fetchAction(
    Result<FeeFineNoticeContext> result) {

    return result.combineAfter(
      context -> actionRepository.findById(context.getNotice().getFeeFineActionId()),
      FeeFineNoticeContext::withAction);
  }

  private CompletableFuture<Result<FeeFineNoticeContext>> fetchAccount(
    Result<FeeFineNoticeContext> result) {

    return result.combineAfter(
      context -> accountRepository.findAccountForAction(context.getAction()),
      FeeFineNoticeContext::withAccount);
  }

  private CompletableFuture<Result<FeeFineNoticeContext>> fetchLoan(
    Result<FeeFineNoticeContext> result) {

    return result.combineAfter(
      // this also fetches user and item
      context -> loanRepository.findLoanForAccount(context.getAccount()),
      FeeFineNoticeContext::withLoan);
  }

  private CompletableFuture<Result<ScheduledNotice>> processNotice(FeeFineNoticeContext context) {
    final ScheduledNotice notice = context.getNotice();

    if (context.isIncomplete()) {
      log.error(getInvalidContextMessage(notice, "one of the referenced entities was not found"));
      return scheduledNoticesRepository.delete(notice);
    } else if (noticeIsIrrelevant(context)) {
      log.warn(getInvalidContextMessage(notice, "associated fee/fine is already closed"));
      return scheduledNoticesRepository.delete(notice);
    }

    return noticePolicyRepository.lookupPolicyId(context.getLoan().getItem(), context.getLoan().getUser())
      .thenCompose(r -> r.after(policy -> sendNotice(context, policy)))
      .thenCompose(r -> r.after(v -> notice.getConfiguration().isRecurring()
        ? scheduledNoticesRepository.update(getNextRecurringNotice(notice))
        : scheduledNoticesRepository.delete(notice)));
  }

  private CompletableFuture<Result<Void>> sendNotice(FeeFineNoticeContext context,
    CirculationRuleMatch policy) {

    final ScheduledNotice notice = context.getNotice();
    final Loan loan = context.getLoan();
    final ScheduledNoticeConfig configuration = notice.getConfiguration();

    final NoticeLogContextItem logContextItem = NoticeLogContextItem.from(loan)
      .withTemplateId(configuration.getTemplateId())
      .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation());

    final NoticeLogContext noticeLogContext = new NoticeLogContext()
      .withUser(loan.getUser())
      .withAccountId(context.getAccount().getId())
      .withItems(List.of(logContextItem.withNoticePolicyId(policy.getPolicyId())));

    final JsonObject noticeContextJson = buildNoticeContextJson(context);

    return patronNoticeService.acceptScheduledNoticeEvent(configuration,
      notice.getRecipientUserId(), noticeContextJson, noticeLogContext);
  }

  private static boolean noticeIsIrrelevant(FeeFineNoticeContext context) {
    return context.getAccount().isClosed() &&
      !context.getNotice().getTriggeringEvent().isAutomaticFeeFineAdjustment();
  }

  private static String getInvalidContextMessage(ScheduledNotice notice, String reason) {
    return String.format(
      "Scheduled \"%s\" notice with id %s for user %s will be deleted without sending: %s",
      notice.getTriggeringEvent().getRepresentation(), notice.getId(), notice.getRecipientUserId(),
      reason);
  }

  private static JsonObject buildNoticeContextJson(FeeFineNoticeContext context) {
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

  @With
  @Getter
  @AllArgsConstructor
  private static class FeeFineNoticeContext {
    private final ScheduledNotice notice;
    private Account account;
    private FeeFineAction action;
    private Loan loan;

    private FeeFineNoticeContext(ScheduledNotice notice) {
      this.notice = notice;
    }

    private boolean isComplete() {
      return allNotNull(action, account, loan)
        && loan.getUser() != null
        && loan.getItem().isFound();
    }

    private boolean isIncomplete() {
      return !isComplete();
    }
  }
}
