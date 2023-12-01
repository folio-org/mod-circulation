package org.folio.circulation.domain.notice.schedule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.policy.RemindersPolicy.ReminderConfig;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.Clients;

import java.lang.invoke.MethodHandles;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

public class ReminderFeeScheduledNoticeService {

  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final CalendarRepository calendarRepository;


  public ReminderFeeScheduledNoticeService (Clients clients) {
    this.scheduledNoticesRepository = ScheduledNoticesRepository.using(clients);
    this.calendarRepository = new CalendarRepository(clients);
  }

  public Result<LoanAndRelatedRecords> scheduleFirstReminder(LoanAndRelatedRecords records) {
    Loan loan = records.getLoan();
    if (loan.getOverdueFinePolicy().isReminderFeesPolicy()) {
      ReminderConfig firstReminder =
        loan.getOverdueFinePolicy().getRemindersPolicy().getFirstReminder();
      instantiateFirstScheduledNotice(records, firstReminder)
        .thenAccept(r -> r.after(scheduledNoticesRepository::create));
    } else {
      log.debug("The current item, barcode {}, is not subject to a reminder fees policy.", loan.getItem().getBarcode());
    }
    return succeeded(records);
  }

  private CompletableFuture<Result<ScheduledNotice>> instantiateFirstScheduledNotice(
    LoanAndRelatedRecords loanRecords,
    ReminderConfig reminderConfig) {

    final Loan loan = loanRecords.getLoan();

    return reminderConfig.nextNoticeDueOn(
        loan.getDueDate(), loanRecords.getTimeZone(), loan.getCheckoutServicePointId(), calendarRepository)
      .thenCompose(nextDueTime ->
        ofAsync(
          new ScheduledNotice(
            UUID.randomUUID().toString(),
            loan.getId(),
            null,
            loan.getUserId(),
            null,
            null,
            TriggeringEvent.DUE_DATE_WITH_REMINDER_FEE,
            nextDueTime.value(),
            instantiateNoticeConfig(reminderConfig))));
  }

  private ScheduledNoticeConfig instantiateNoticeConfig(ReminderConfig reminderConfig) {
    return new ScheduledNoticeConfig(
      NoticeTiming.AFTER,
      null, // recurrence handled using reminder fee policy
      reminderConfig.getNoticeTemplateId(),
      reminderConfig.getNoticeFormat(),
      true);
  }


}
