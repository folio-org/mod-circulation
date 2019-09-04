package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ConfigurationRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class ScheduledDueDateNoticeHandler {

  public static ScheduledDueDateNoticeHandler using(Clients clients, DateTime systemTime) {
    return new ScheduledDueDateNoticeHandler(
      new LoanRepository(clients),
      new LoanPolicyRepository(clients),
      new ConfigurationRepository(clients),
      PatronNoticeService.using(clients),
      ScheduledNoticesRepository.using(clients),
      systemTime);
  }

  private LoanRepository loanRepository;
  private LoanPolicyRepository loanPolicyRepository;
  private ConfigurationRepository configurationRepository;
  private PatronNoticeService patronNoticeService;
  private ScheduledNoticesRepository scheduledNoticesRepository;
  private DateTime systemTime;

  public ScheduledDueDateNoticeHandler(
    LoanRepository loanRepository, LoanPolicyRepository loanPolicyRepository,
    ConfigurationRepository configurationRepository,
    PatronNoticeService patronNoticeService,
    ScheduledNoticesRepository scheduledNoticesRepository, DateTime systemTime) {

    this.loanRepository = loanRepository;
    this.loanPolicyRepository = loanPolicyRepository;
    this.configurationRepository = configurationRepository;
    this.patronNoticeService = patronNoticeService;
    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.systemTime = systemTime;
  }

  public CompletableFuture<Result<Collection<ScheduledNotice>>> handleNotices(Collection<ScheduledNotice> scheduledNotices) {
    CompletableFuture<Result<ScheduledNotice>> future = completedFuture(succeeded(null));
    for (ScheduledNotice scheduledNotice : scheduledNotices) {
      future = future.thenCompose(r -> r.after(v -> handleNotice(scheduledNotice)));
    }
    return future.thenApply(r -> r.map(v -> scheduledNotices));
  }

  private CompletableFuture<Result<ScheduledNotice>> handleNotice(ScheduledNotice notice) {
    if (notice.getLoanId() != null) {
      return handleDueDateNotice(notice);
    }
    return completedFuture(succeeded(notice));
  }

  private CompletableFuture<Result<ScheduledNotice>> handleDueDateNotice(ScheduledNotice notice) {
    return loanRepository.getById(notice.getLoanId())
      .thenApply(r -> r.map(LoanAndRelatedRecords::new))
      .thenCompose(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenCompose(configurationRepository::lookupTimeZoneForLoanRelatedRecords)
      .thenCompose(r -> r.after(records -> sendNotice(records, notice)))
      .thenCompose(r -> r.after(relatedRecords -> updateNotice(relatedRecords, notice)));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> sendNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNotice notice) {
    Loan loan = relatedRecords.getLoan();

    if (loan.isClosed() || beforeNoticeIsNotRelevant(notice, loan)) {
      return completedFuture(succeeded(relatedRecords));
    }

    JsonObject loanNoticeContext = TemplateContextUtil.createLoanNoticeContext(loan);

    return patronNoticeService.acceptScheduledNoticeEvent(
      notice.getConfiguration(), relatedRecords.getUserId(), loanNoticeContext)
      .thenApply(r -> r.map(v -> relatedRecords));
  }

  private CompletableFuture<Result<ScheduledNotice>> updateNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNotice notice) {
    Loan loan = relatedRecords.getLoan();
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    if (loan.isClosed() || !noticeConfig.isRecurring()) {
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

  private boolean beforeNoticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return noticeConfig.getTiming() == NoticeTiming.BEFORE &&
      loan.getDueDate().isBefore(systemTime);
  }

  private boolean nextRecurringNoticeIsNotRelevant(
    ScheduledNotice notice, Loan loan) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return noticeConfig.isRecurring() &&
      noticeConfig.getTiming() == NoticeTiming.BEFORE &&
      notice.getNextRunTime().isAfter(loan.getDueDate());
  }
}
