package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ConfigurationRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.notice.NoticeContextUtil;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class ScheduledNoticeHandler {

  public static ScheduledNoticeHandler using(Clients clients) {
    return new ScheduledNoticeHandler(
      new LoanRepository(clients),
      new LoanPolicyRepository(clients),
      new ConfigurationRepository(clients),
      PatronNoticeService.using(clients),
      ScheduledNoticeRepository.using(clients));
  }

  private LoanRepository loanRepository;
  private LoanPolicyRepository loanPolicyRepository;
  private ConfigurationRepository configurationRepository;
  private PatronNoticeService patronNoticeService;
  private ScheduledNoticeRepository scheduledNoticeRepository;

  public ScheduledNoticeHandler(
    LoanRepository loanRepository, LoanPolicyRepository loanPolicyRepository,
    ConfigurationRepository configurationRepository,
    PatronNoticeService patronNoticeService,
    ScheduledNoticeRepository scheduledNoticeRepository) {

    this.loanRepository = loanRepository;
    this.loanPolicyRepository = loanPolicyRepository;
    this.configurationRepository = configurationRepository;
    this.patronNoticeService = patronNoticeService;
    this.scheduledNoticeRepository = scheduledNoticeRepository;
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
      .thenCompose(r -> r.after(configurationRepository::lookupTimeZone))
      .thenCompose(r -> r.after(records -> sendNotice(records, notice.getNoticeConfig())))
      .thenCompose(r -> r.after(relatedRecords -> updateNotice(relatedRecords, notice)));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> sendNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNoticeConfig noticeConfig) {

    if (noticeIsNotRelevant(noticeConfig, relatedRecords.getLoan())) {
      return completedFuture(succeeded(relatedRecords));
    }

    JsonObject loanNoticeContext = NoticeContextUtil.createLoanNoticeContext(
      relatedRecords.getLoan(),
      relatedRecords.getLoanPolicy(),
      relatedRecords.getTimeZone());
    return patronNoticeService.acceptScheduledNoticeEvent(
      noticeConfig, relatedRecords.getUserId(), loanNoticeContext)
      .thenApply(r -> r.map(v -> relatedRecords));
  }

  private CompletableFuture<Result<ScheduledNotice>> updateNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNotice notice) {
    Loan loan = relatedRecords.getLoan();
    ScheduledNoticeConfig noticeConfig = notice.getNoticeConfig();

    if (noticeIsNotRelevant(noticeConfig, loan) || !noticeConfig.isRecurring()) {
      return scheduledNoticeRepository.delete(notice);
    }

    ScheduledNotice nextRecurringNotice = notice.withNextRunTime(
      notice.getNextRunTime()
        .plus(noticeConfig.getRecurringPeriod().timePeriod())
    );

    return scheduledNoticeRepository.update(nextRecurringNotice);
  }

  private boolean noticeIsNotRelevant(ScheduledNoticeConfig noticeConfig, Loan loan) {
    return loan.isClosed() || beforeRecurringNoticeIsNotRelevant(noticeConfig, loan);
  }

  private boolean beforeRecurringNoticeIsNotRelevant(
    ScheduledNoticeConfig noticeConfig, Loan loan) {
    return
      noticeConfig.getTiming() == NoticeTiming.BEFORE &&
        noticeConfig.isRecurring() &&
        DateTime.now().isAfter(loan.getDueDate());
  }
}
