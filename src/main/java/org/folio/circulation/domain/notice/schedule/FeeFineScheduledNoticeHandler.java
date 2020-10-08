package org.folio.circulation.domain.notice.schedule;

import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createFeeFineNoticeContext;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
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
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class FeeFineScheduledNoticeHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PatronNoticeService patronNoticeService;
  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final FeeFineActionRepository actionRepository;
  private final AccountRepository accountRepository;
  private final LoanRepository loanRepository;
  private PatronNoticePolicyRepository noticePolicyRepository;

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
    } else if (context.getAccount().isClosed()) {
      log.warn(getInvalidContextMessage(notice, "associated fee/fine is already closed"));
    } else {
      JsonObject noticeContext = createFeeFineNoticeContext(context.getAccount(), context.getLoan());
      ScheduledNoticeConfig config = notice.getConfiguration();

      NoticeLogContextItem logContextItem = NoticeLogContextItem.from(context.getLoan())
        .withTemplateId(notice.getConfiguration().getTemplateId())
        .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation());

      return noticePolicyRepository.lookupPolicyId(
        context.getLoan().getItem(), context.getLoan().getUser())
        .thenCompose(r -> r.after(policy -> patronNoticeService.acceptScheduledNoticeEvent(
          config, notice.getRecipientUserId(), noticeContext,
          new NoticeLogContext().withUser(context.getLoan().getUser())
            .withFeeFineAction(context.getAction())
            .withItems(Collections.singletonList(logContextItem.withNoticePolicyId(policy.getPolicyId()))))))
        .thenCompose(r -> r.after(v -> {
          if (config.isRecurring()) {
            return scheduledNoticesRepository.update(getNextRecurringNotice(notice));
          }
          return scheduledNoticesRepository.delete(notice);
        }));
    }

    return scheduledNoticesRepository.delete(notice);
  }

  private static String getInvalidContextMessage(ScheduledNotice notice, String reason) {
    return String.format(
      "Scheduled \"%s\" notice with id %s for user %s will be deleted without sending: %s",
      notice.getTriggeringEvent().getRepresentation(), notice.getId(), notice.getRecipientUserId(),
      reason);
  }

  private ScheduledNotice getNextRecurringNotice(ScheduledNotice notice) {
    Period recurringPeriod = notice.getConfiguration().getRecurringPeriod().timePeriod();
    DateTime nextRunTime = notice.getNextRunTime().plus(recurringPeriod);
    DateTime now = getClockManager().getDateTime();

    if (nextRunTime.isBefore(now)) {
      nextRunTime = now.plus(recurringPeriod);
    }

    return notice.withNextRunTime(nextRunTime);
  }

  private static class FeeFineNoticeContext {
    private final ScheduledNotice notice;
    private Account account;
    private FeeFineAction action;
    private Loan loan;

    private FeeFineNoticeContext(ScheduledNotice notice) {
      this.notice = notice;
    }

    private FeeFineNoticeContext(ScheduledNotice notice, Account account,
      FeeFineAction action, Loan loan) {
      this.notice = notice;
      this.account = account;
      this.action = action;
      this.loan = loan;
    }

    private ScheduledNotice getNotice() {
      return notice;
    }

    private Account getAccount() {
      return account;
    }

    private FeeFineAction getAction() {
      return action;
    }

    private Loan getLoan() {
      return loan;
    }

    private FeeFineNoticeContext withAccount(Account account) {
      return new FeeFineNoticeContext(this.notice, account, this.action, this.loan);
    }

    private FeeFineNoticeContext withAction(FeeFineAction action) {
      return new FeeFineNoticeContext(this.notice, this.account, action, this.loan);
    }

    private FeeFineNoticeContext withLoan(Loan loan) {
      return new FeeFineNoticeContext(this.notice, this.account, action, loan);
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
