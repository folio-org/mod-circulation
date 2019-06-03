package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.support.Result.succeeded;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.Period;

public class ScheduledNoticeService {

  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;

  public ScheduledNoticeService(
    ScheduledNoticesRepository scheduledNoticesRepository,
    PatronNoticePolicyRepository noticePolicyRepository) {
    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.noticePolicyRepository = noticePolicyRepository;
  }

  public Result<LoanAndRelatedRecords> scheduleNoticesForLoanDueDate(
    LoanAndRelatedRecords relatedRecords) {
    scheduleNoticesForLoanDueDate(relatedRecords.getLoan());
    return succeeded(relatedRecords);
  }

  private void scheduleNoticesForLoanDueDate(Loan loan) {
    noticePolicyRepository.lookupPolicy(loan)
      .thenAccept(r -> r.next(policy -> scheduleDueDateNoticesBasedOnPolicy(loan, policy)));
  }

  private Result<PatronNoticePolicy> scheduleDueDateNoticesBasedOnPolicy(
    Loan loan, PatronNoticePolicy noticePolicy) {

    List<ScheduledNotice> scheduledNotices = noticePolicy.getNoticeConfigurations().stream()
      .filter(c -> c.getNoticeEventType() == NoticeEventType.DUE_DATE)
      .map(c -> createDueDateScheduledNotice(c, loan))
      .collect(Collectors.toList());

    scheduledNotices.forEach(scheduledNoticesRepository::create);
    return succeeded(noticePolicy);
  }

  private ScheduledNotice createDueDateScheduledNotice(NoticeConfiguration configuration, Loan loan) {
    return new ScheduledNoticeBuilder()
      .setId(UUID.randomUUID().toString())
      .setLoanId(loan.getId())
      .setNextRunTime(determineNextRunTime(configuration, loan))
      .setNoticeConfig(createScheduledNoticeConfig(configuration))
      .build();
  }

  private DateTime determineNextRunTime(NoticeConfiguration configuration, Loan loan) {
    if (configuration.getTiming() == NoticeTiming.UPON_AT) {
      return loan.getDueDate();
    }
    DateTime dueDate = loan.getDueDate();
    Period timingPeriod = configuration.getTimingPeriod().timePeriod();

    switch (configuration.getTiming()) {
      case BEFORE:
        return dueDate.minus(timingPeriod);
      case AFTER:
        return dueDate.plus(timingPeriod);
      default:
        return dueDate;
    }
  }

  private ScheduledNoticeConfig createScheduledNoticeConfig(NoticeConfiguration configuration) {
    return new ScheduledNoticeConfigBuilder()
      .setTemplateId(configuration.getTemplateId())
      .setTiming(configuration.getTiming())
      .setFormat(configuration.getNoticeFormat())
      .setRecurringPeriod(configuration.getRecurringPeriod())
      .setSendInRealTime(configuration.sendInRealTime())
      .build();
  }
}
