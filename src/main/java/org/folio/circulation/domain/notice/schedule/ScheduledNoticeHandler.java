package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Collection;
import java.util.UUID;
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
      .thenCompose(r -> r.after(relatedRecords -> createNextRecurringNotice(relatedRecords, notice)))
      .thenCompose(r -> r.after(v -> scheduledNoticeRepository.delete(notice)));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> sendNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNoticeConfig noticeConfig) {

    if (relatedRecords.getLoan().isClosed()) {
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

  private CompletableFuture<Result<ScheduledNotice>> createNextRecurringNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNotice scheduledNotice) {
    Loan loan = relatedRecords.getLoan();
    ScheduledNoticeConfig noticeConfig = scheduledNotice.getNoticeConfig();

    if (!noticeConfig.isRecurring() || loan.isClosed()) {
      return completedFuture(succeeded(scheduledNotice));
    }

    if (noticeConfig.getTiming() == NoticeTiming.BEFORE &&
      DateTime.now().isAfter(loan.getDueDate())) {
      return completedFuture(succeeded(scheduledNotice));
    }

    ScheduledNotice nextRecurringNotice = new ScheduledNoticeBuilder()
      .setId(UUID.randomUUID().toString())
      .setLoanId(loan.getId())
      .setNextRunTime(scheduledNotice.getNextRunTime()
        .plus(noticeConfig.getRecurringPeriod().timePeriod()))
      .setNoticeConfig(noticeConfig)
      .build();

    return scheduledNoticeRepository.create(nextRecurringNotice);
  }
}
