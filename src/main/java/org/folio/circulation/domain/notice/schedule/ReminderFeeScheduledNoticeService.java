package org.folio.circulation.domain.notice.schedule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.policy.OverdueFinePolicyRemindersPolicy;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.UUID;

import static org.folio.circulation.support.results.Result.succeeded;

public class ReminderFeeScheduledNoticeService {

  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ScheduledNoticesRepository scheduledNoticesRepository;

  public ReminderFeeScheduledNoticeService (ScheduledNoticesRepository scheduledNoticesRepository) {
    this.scheduledNoticesRepository = scheduledNoticesRepository;
  }

  public Result<LoanAndRelatedRecords> scheduleFirstReminder(LoanAndRelatedRecords records) {
    Loan loan = records.getLoan();
    if (loan.getOverdueFinePolicy().isReminderFeesPolicy()) {

      OverdueFinePolicyRemindersPolicy.ReminderSequenceEntry firstReminder =
        loan.getOverdueFinePolicy().getRemindersPolicy().getReminderSequenceEntry(1);

      ScheduledNoticeConfig config =
        new ScheduledNoticeConfig(
          NoticeTiming.AFTER,
          null, // recurrence handled using reminder fee policy
          firstReminder.getNoticeTemplateId(),
          firstReminder.getNoticeFormat(),
          true);

      ScheduledNotice scheduledNotice = new ScheduledNotice(
        UUID.randomUUID().toString(),
        loan.getId(),
        null,
        loan.getUserId(),
        null,
        null,
        TriggeringEvent.DUE_DATE_WITH_REMINDER_FEE,
        firstReminder.getPeriod().plusDate(loan.getDueDate()),
        config
      );

      scheduledNoticesRepository.create(scheduledNotice);
    } else {
      log.debug("The current item, barcode {}, is not subject to a reminder fees policy.", loan.getItem().getBarcode());
    }
    return succeeded(records);
  }
}
