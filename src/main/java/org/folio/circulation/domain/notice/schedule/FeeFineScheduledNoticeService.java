package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.NoticeEventType.OVERDUE_FINE_RENEWED;
import static org.folio.circulation.domain.notice.NoticeEventType.OVERDUE_FINE_RETURNED;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

public class FeeFineScheduledNoticeService {
  public static FeeFineScheduledNoticeService using(Clients clients) {
    return new FeeFineScheduledNoticeService(
      ScheduledNoticesRepository.using(clients),
      new PatronNoticePolicyRepository(clients));
  }

  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;

  public FeeFineScheduledNoticeService(
    ScheduledNoticesRepository scheduledNoticesRepository,
    PatronNoticePolicyRepository noticePolicyRepository) {
    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.noticePolicyRepository = noticePolicyRepository;
  }

  public Result<CheckInProcessRecords> scheduleNotices(
    CheckInProcessRecords records, FeeFineAction action) {

    scheduleNotices(records.getLoan(), action, OVERDUE_FINE_RETURNED);

    return succeeded(records);
  }

  public Result<RenewalContext> scheduleOverdueFineNotices(RenewalContext records) {
    scheduleNotices(records.getLoan(), records.getOverdueFeeFineAction(), OVERDUE_FINE_RENEWED);

    return succeeded(records);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> scheduleNotices(
    Loan loan, FeeFineAction action, NoticeEventType eventType) {

    if (action == null) {
      return ofAsync(() -> null);
    }

    return noticePolicyRepository.lookupPolicy(loan)
      .thenCompose(r -> r.after(policy ->
        scheduleNoticeBasedOnPolicy(loan, policy, action, eventType)));
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> scheduleNoticeBasedOnPolicy(
    Loan loan, PatronNoticePolicy noticePolicy, FeeFineAction action, NoticeEventType eventType) {

    List<ScheduledNotice> scheduledNotices = noticePolicy.getNoticeConfigurations().stream()
      .filter(config -> config.getNoticeEventType() == eventType)
      .map(config -> createScheduledNotice(config, loan, action, eventType))
      .collect(Collectors.toList());

    return allOf(scheduledNotices, scheduledNoticesRepository::create);
  }

  private ScheduledNotice createScheduledNotice(NoticeConfiguration configuration,
    Loan loan, FeeFineAction action, NoticeEventType eventType) {

    return new ScheduledNoticeBuilder()
      .setId(UUID.randomUUID().toString())
      .setLoanId(loan.getId())
      .setFeeFineActionId(action.getId())
      .setRecipientUserId(loan.getUserId())
      .setNextRunTime(determineNextRunTime(configuration, action))
      .setNoticeConfig(createScheduledNoticeConfig(configuration))
      .setTriggeringEvent(TriggeringEvent.from(eventType.getRepresentation()))
      .build();
  }

  private DateTime determineNextRunTime(NoticeConfiguration configuration, FeeFineAction action) {
    DateTime actionDateTime = action.getDateAction();

    return configuration.getTiming() == NoticeTiming.AFTER
      ? actionDateTime.plus(configuration.getTimingPeriod().timePeriod())
      : actionDateTime;
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
