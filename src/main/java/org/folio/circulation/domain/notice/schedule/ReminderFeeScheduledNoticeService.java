package org.folio.circulation.domain.notice.schedule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.RemindersPolicy.ReminderConfig;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.Clients;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.results.Result.succeeded;

public class ReminderFeeScheduledNoticeService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final CalendarRepository calendarRepository;

  public ReminderFeeScheduledNoticeService(Clients clients) {
    this.scheduledNoticesRepository = ScheduledNoticesRepository.using(clients);
    this.calendarRepository = new CalendarRepository(clients);
  }

  /**
   * Schedules first reminder after checkout
   * @param records the objects passed in from the check-out chain of procedures
   * @return the (unmodified) objects handed back to the check-out chain of procedures
   */
  public Result<LoanAndRelatedRecords> scheduleFirstReminder(LoanAndRelatedRecords records) {
    log.debug("scheduleFirstReminder:: scheduling first reminder for loan {}",
      records.getLoan()::getId);
    Loan loan = records.getLoan();
    if (loan.getOverdueFinePolicy().isReminderFeesPolicy()) {
      scheduleFirstReminder(loan, records.getTimeZone());
    } else {
      log.info("scheduleFirstReminder:: item with barcode {} is not subject to reminder fees policy, skipping",
        loan.getItem() != null ? loan.getItem().getBarcode() : "null");
    }
    return succeeded(records);
  }

  private Result<Void> scheduleFirstReminder(Loan loan, ZoneId timeZone) {
    log.debug("scheduleFirstReminder:: scheduling first reminder for loan {} in timezone {}",
      loan::getId, () -> timeZone);
    ReminderConfig firstReminder = loan.getOverdueFinePolicy().getRemindersPolicy().getFirstReminder();
    instantiateFirstScheduledNotice(loan, timeZone, firstReminder).thenAccept(
      r -> r.after(scheduledNoticesRepository::create));
    return succeeded(null);
  }

  /**
   * Re-schedules first reminder after manual due date change or recall
   * @param relatedRecords context records passed in from the manual due date change or recall process
   * @return the due date change context back to the manual due date change or recall process
   */
  public Result<LoanAndRelatedRecords> rescheduleFirstReminder(LoanAndRelatedRecords relatedRecords) {
    log.debug("rescheduleFirstReminder:: rescheduling first reminder for loan {}",
      relatedRecords.getLoan()::getId);
    return rescheduleFirstReminder(relatedRecords.getLoan(), relatedRecords.getTimeZone(), relatedRecords);
  }

  /**
   * Re-schedules first reminder after renewal
   * @param renewalContext passed in from the renewal process
   * @return the renewal context back to the renewal process
   */
  public Result<RenewalContext> rescheduleFirstReminder(RenewalContext renewalContext) {
    log.debug("rescheduleFirstReminder:: rescheduling first reminder after renewal for loan {}",
      renewalContext.getLoan()::getId);
    return rescheduleFirstReminder(renewalContext.getLoan(), renewalContext.getTimeZone(), renewalContext);
  }

  private <T> Result<T> rescheduleFirstReminder(Loan loan, ZoneId timeZone, T mapTo) {
    log.debug("rescheduleFirstReminder:: processing reminder reschedule for loan {}", loan::getId);
    OverdueFinePolicy policy = loan.getOverdueFinePolicy();
    if (policy.isReminderFeesPolicy()) {
      log.info("rescheduleFirstReminder:: deleting existing reminder notices for loan {}", loan::getId);
      scheduledNoticesRepository.deleteByLoanIdAndTriggeringEvent(loan.getId(),
          TriggeringEvent.DUE_DATE_WITH_REMINDER_FEE)
        .thenAccept(r -> r.next(deleted -> scheduleFirstReminder(loan, timeZone)));
    } else {
      log.info("rescheduleFirstReminder:: item with barcode {} is not subject to reminder fees policy, skipping reschedule",
        loan.getItem() != null ? loan.getItem().getBarcode() : "null");
    }
    return succeeded(mapTo);
  }

  private CompletableFuture<Result<ScheduledNotice>> instantiateFirstScheduledNotice(
    Loan loan, ZoneId timeZone, ReminderConfig reminderConfig) {

    log.debug("instantiateFirstScheduledNotice:: creating first scheduled notice for loan {} in timezone {}",
      loan::getId, () -> timeZone);
    return reminderConfig.nextNoticeDueOn(loan.getDueDate(), timeZone,
        loan.getCheckoutServicePointId(), calendarRepository)
      .thenApply(r -> {
        if (r.succeeded()) {
          log.info("instantiateFirstScheduledNotice:: scheduled notice created for loan {}", loan::getId);
        }
        return r.map(nextDueTime -> new ScheduledNotice(UUID.randomUUID().toString(),
          loan.getId(), null, loan.getUserId(), null, null,
          TriggeringEvent.DUE_DATE_WITH_REMINDER_FEE, nextDueTime,
          instantiateNoticeConfig(reminderConfig)));
      });
  }

  private ScheduledNoticeConfig instantiateNoticeConfig(ReminderConfig reminderConfig) {
    log.debug("instantiateNoticeConfig:: creating notice config with template {}",
      reminderConfig::getNoticeTemplateId);
    return new ScheduledNoticeConfig(NoticeTiming.AFTER, null,
      reminderConfig.getNoticeTemplateId(), reminderConfig.getNoticeFormat(), true);
  }

}
