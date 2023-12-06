package org.folio.circulation.domain.notice.schedule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.policy.RemindersPolicy.ReminderConfig;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
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

  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final CalendarRepository calendarRepository;
  final OverdueFinePolicyRepository overdueFinePolicyRepository;

  public ReminderFeeScheduledNoticeService(Clients clients) {
    this.scheduledNoticesRepository = ScheduledNoticesRepository.using(clients);
    this.calendarRepository = new CalendarRepository(clients);
    this.overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
  }

  /**
   * Used for scheduling first reminder after checkout
   * @param records the objects passed in from the check-out chain of procedures
   * @return the (unmodified) objects handed back to the check-out chain of procedures
   */
  public Result<LoanAndRelatedRecords> scheduleFirstReminder(LoanAndRelatedRecords records) {
    log.debug("scheduleFirstReminder:: parameters loanAndRelatedRecords: {}",
      () -> records);
    Loan loan = records.getLoan();
    if (loan.getOverdueFinePolicy().isReminderFeesPolicy()) {
      scheduleFirstReminder(loan, records.getTimeZone());
    } else {
      log.debug("The current item, barcode {}, is not subject to a reminder fees policy.", loan.getItem().getBarcode());
    }
    return succeeded(records);
  }

  /**
   * Used for scheduling first reminder after renewal
   * @param renewalContext the objects passed in from the renewal chain of procedures
   * @return the (unmodified) objects handed back to the renewal chain of procedures
   */
  public Result<RenewalContext> rescheduleFirstReminder(RenewalContext renewalContext) {
    Loan loan = renewalContext.getLoan();
    overdueFinePolicyRepository.lookupPolicy(loan)
      .thenApply(policyResult -> policyResult.next(policy -> {
        if (policy.isReminderFeesPolicy()) {
          log.debug("Renewal: Loan has reminder policy. Barcode: {}. Renewal context: {}.",
            loan.getItem().getBarcode(), renewalContext);
          scheduledNoticesRepository.deleteByLoanIdAndTriggeringEvent(
              loan.getId(), TriggeringEvent.DUE_DATE_WITH_REMINDER_FEE)
            .thenAccept(deleteResult -> deleteResult.next(response ->
              scheduleFirstReminder(loan.withOverdueFinePolicy(policy), renewalContext.getTimeZone())));
        } else {
          log.debug("The current item, barcode {}, is not subject to a reminder fees policy.",
            loan.getItem().getBarcode());
        }
        return succeeded(renewalContext);
      }));
    return succeeded(renewalContext);
  }

  private Result<Void> scheduleFirstReminder(Loan loan, ZoneId timeZone) {
    log.debug("scheduleFirstReminder:: parameters loan: {}, timeZone: {}",
      () -> loan, () -> timeZone);

    ReminderConfig firstReminder = loan.getOverdueFinePolicy().getRemindersPolicy().getFirstReminder();
    instantiateFirstScheduledNotice(loan, timeZone, firstReminder).thenAccept(
      r -> r.after(scheduledNoticesRepository::create));
    return succeeded(null);
  }

  private CompletableFuture<Result<ScheduledNotice>> instantiateFirstScheduledNotice(
    Loan loan,
    ZoneId timeZone,
    ReminderConfig reminderConfig) {

    log.debug("instantiateFirstScheduledNotice:: parameters loan: {}, timeZone: {}, " +
        "reminderConfig: {}", () -> loan, () -> timeZone, () -> reminderConfig);

    return reminderConfig.nextNoticeDueOn(loan.getDueDate(), timeZone,
        loan.getCheckoutServicePointId(), calendarRepository)
      .thenApply(r -> r.next(nextDueTime -> succeeded(new ScheduledNotice(UUID.randomUUID().toString(),
        loan.getId(), null, loan.getUserId(), null, null,
        TriggeringEvent.DUE_DATE_WITH_REMINDER_FEE, nextDueTime,
        instantiateNoticeConfig(reminderConfig)))));
  }

  private ScheduledNoticeConfig instantiateNoticeConfig(ReminderConfig reminderConfig) {
    log.debug("instantiateNoticeConfig:: parameters: {}", () -> reminderConfig);
    return new ScheduledNoticeConfig(NoticeTiming.AFTER, null,
      reminderConfig.getNoticeTemplateId(), reminderConfig.getNoticeFormat(), true);
  }

}
