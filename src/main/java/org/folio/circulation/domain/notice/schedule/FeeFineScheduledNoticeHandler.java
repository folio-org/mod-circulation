package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.TemplateContextUtil.createFeeFineNoticeContext;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.folio.circulation.support.Result.ofAsync;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.FeeFineActionRepository;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.Period;

import io.vertx.core.json.JsonObject;

public class FeeFineScheduledNoticeHandler {
  private final PatronNoticeService patronNoticeService;
  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final FeeFineActionRepository actionRepository;
  private final AccountRepository accountRepository;

  private FeeFineScheduledNoticeHandler(PatronNoticeService patronNoticeService,
    ScheduledNoticesRepository scheduledNoticesRepository,
    FeeFineActionRepository actionRepository,
    AccountRepository accountRepository) {

    this.patronNoticeService = patronNoticeService;
    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.actionRepository = actionRepository;
    this.accountRepository = accountRepository;
  }

  public static FeeFineScheduledNoticeHandler using(Clients clients) {
    return new FeeFineScheduledNoticeHandler(
      PatronNoticeService.using(clients),
      ScheduledNoticesRepository.using(clients),
      new FeeFineActionRepository(clients),
      new AccountRepository(clients));
  }

  public CompletableFuture<Result<List<ScheduledNotice>>> handleNotices(
    Collection<ScheduledNotice> scheduledNotices) {

    return allOf(scheduledNotices, this::handleNotice);
  }

  public CompletableFuture<Result<ScheduledNotice>> handleNotice(ScheduledNotice notice) {
    return ofAsync(FeeFineRelatedRecords::new)
      .thenCompose(r -> r.combineAfter(records ->
        actionRepository.findById(notice.getFeeFineActionId()),
        FeeFineRelatedRecords::withAction))
      .thenCompose(r -> r.combineAfter(this::fetchAccount, FeeFineRelatedRecords::withAccount))
      .thenCompose(r -> r.after(records -> processNotice(notice, records)));
  }

  private CompletableFuture<Result<Account>> fetchAccount(FeeFineRelatedRecords records) {
    FeeFineAction action = records.getAction();

    return action != null
      ? accountRepository.findById(action.getAccountId())
      : ofAsync(() -> null);
  }

  private CompletableFuture<Result<ScheduledNotice>> processNotice(
    ScheduledNotice notice, FeeFineRelatedRecords records) {

    if (records.isValid()) {
      FeeFineAction action = records.getAction();
      JsonObject noticeContext = createFeeFineNoticeContext(records.getAccount(), action);
      ScheduledNoticeConfig config = notice.getConfiguration();

      patronNoticeService.acceptScheduledNoticeEvent(config, action.getUserId(), noticeContext);

      if (config.isRecurring()) {
        return scheduledNoticesRepository.update(getNextRecurringNotice(notice));
      }
    }

    return scheduledNoticesRepository.delete(notice);
  }

  private ScheduledNotice getNextRecurringNotice(ScheduledNotice notice) {
    Period recurringPeriod = notice.getConfiguration().getRecurringPeriod().timePeriod();
    DateTime nextRunTime = notice.getNextRunTime().plus(recurringPeriod);

    if (nextRunTime.isBeforeNow()) {
      nextRunTime = getClockManager().getDateTime().plus(recurringPeriod);
    }

    return notice.withNextRunTime(nextRunTime);
  }

  private static class FeeFineRelatedRecords {
    private Account account;
    private FeeFineAction action;

    private FeeFineRelatedRecords(Account account, FeeFineAction action) {
      this.account = account;
      this.action = action;
    }

    public FeeFineRelatedRecords() {
    }

    public Account getAccount() {
      return account;
    }

    public FeeFineAction getAction() {
      return action;
    }

    public FeeFineRelatedRecords withAccount(Account account) {
      return new FeeFineRelatedRecords(account, this.action);
    }

    public FeeFineRelatedRecords withAction(FeeFineAction action) {
      return new FeeFineRelatedRecords(this.account, action);
    }

    public boolean isValid() {
      return ObjectUtils.allNotNull(action, account) && account.isOpen();
    }
  }
}
