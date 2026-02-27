package org.folio.circulation.domain.notice.schedule;
import java.lang.invoke.MethodHandles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.folio.circulation.domain.notice.NoticeEventType.AGED_TO_LOST;
import static org.folio.circulation.domain.notice.NoticeEventType.DUE_DATE;
import static org.folio.circulation.support.results.Result.succeeded;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.UUID;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class LoanScheduledNoticeService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public static LoanScheduledNoticeService using(Clients clients) {
    return new LoanScheduledNoticeService(
      ScheduledNoticesRepository.using(clients),
      new PatronNoticePolicyRepository(clients));
  }

  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;

  public LoanScheduledNoticeService(ScheduledNoticesRepository scheduledNoticesRepository,
    PatronNoticePolicyRepository noticePolicyRepository) {

    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.noticePolicyRepository = noticePolicyRepository;
  }

  public Result<LoanAndRelatedRecords> scheduleNoticesForLoanDueDate(LoanAndRelatedRecords records) {
    log.info("scheduleNoticesForLoanDueDate:: scheduling due date notices for loan {}",
      records.getLoan()::getId);
    Loan loan = records.getLoan();
    scheduleLoanNotices(loan, DUE_DATE, loan.getDueDate());

    return succeeded(records);
  }

  public Result<Void> scheduleAgedToLostNotices(Collection<Loan> loans) {
    log.info("scheduleAgedToLostNotices:: scheduling aged to lost notices for {} loans",
      loans::size);
    loans.forEach(loan -> scheduleLoanNotices(loan, AGED_TO_LOST, loan.getAgedToLostDateTime()));

    return succeeded(null);
  }

  private Result<Void> scheduleLoanNotices(Loan loan, NoticeEventType eventType, ZonedDateTime eventTime) {
    log.info("scheduleLoanNotices:: scheduling loan notices for event type {}, loan {}",
      eventType, loan != null ? loan.getId() : "null");
    noticePolicyRepository.lookupPolicy(loan)
      .thenAccept(r -> r.next(policy ->
        scheduleLoanNoticesBasedOnPolicy(loan, eventType, eventTime, policy)));

    return succeeded(null);
  }

  private Result<PatronNoticePolicy> scheduleLoanNoticesBasedOnPolicy(Loan loan,
     NoticeEventType eventType, ZonedDateTime eventTime, PatronNoticePolicy noticePolicy) {

    noticePolicy.getNoticeConfigurations().stream()
      .filter(config -> config.getNoticeEventType() == eventType)
      .map(config -> createScheduledNotice(config, loan, eventType, eventTime))
      .forEach(scheduledNoticesRepository::create);

    return succeeded(noticePolicy);
  }

  private ScheduledNotice createScheduledNotice(NoticeConfiguration configuration, Loan loan,
    NoticeEventType eventType, ZonedDateTime eventTime) {

    log.info("createScheduledNotice:: creating scheduled notice for event type {}, loan {}",
      () -> eventType, loan::getId);
    return new ScheduledNoticeBuilder()
      .setId(UUID.randomUUID().toString())
      .setLoanId(loan.getId())
      .setRecipientUserId(loan.getUserId())
      .setNextRunTime(determineNextRunTime(configuration, eventTime))
      .setNoticeConfig(createScheduledNoticeConfig(configuration))
      .setTriggeringEvent(TriggeringEvent.from(eventType))
      .build();
  }

  private ZonedDateTime determineNextRunTime(NoticeConfiguration configuration, ZonedDateTime eventTime) {
    log.info("determineNextRunTime:: determining next run time for timing {}",
      configuration::getTiming);
    final NoticeTiming timing = configuration.getTiming();

    switch (timing) {
      case UPON_AT:
        return eventTime;
      case BEFORE:
        return configuration.getTimingPeriod().minusDate(eventTime);
      case AFTER:
        return configuration.getTimingPeriod().plusDate(eventTime);
      default:
        log.error("determineNextRunTime:: unknown patron notice timing: {}", timing);
        throw new IllegalArgumentException("Unknown patron notice timing: " + timing);
    }
  }

  private ScheduledNoticeConfig createScheduledNoticeConfig(NoticeConfiguration configuration) {
    log.info("createScheduledNoticeConfig:: creating scheduled notice config with template {}",
      configuration::getTemplateId);
    return new ScheduledNoticeConfigBuilder()
      .setTemplateId(configuration.getTemplateId())
      .setTiming(configuration.getTiming())
      .setFormat(configuration.getNoticeFormat())
      .setRecurringPeriod(configuration.getRecurringPeriod())
      .setSendInRealTime(configuration.sendInRealTime())
      .build();
  }

  public Result<LoanAndRelatedRecords> rescheduleDueDateNotices(LoanAndRelatedRecords relatedRecords) {
    log.info("rescheduleDueDateNotices:: rescheduling due date notices for loan {}",
      relatedRecords.getLoan() != null ? relatedRecords.getLoan().getId() : "null");
    return rescheduleDueDateNotices(relatedRecords.getLoan(), relatedRecords);
  }

  public Result<RenewalContext> rescheduleDueDateNotices(RenewalContext renewalContext) {
    log.info("rescheduleDueDateNotices:: rescheduling due date notices for renewal context, loan {}",
      renewalContext.getLoan() != null ? renewalContext.getLoan().getId() : "null");
    return rescheduleDueDateNotices(renewalContext.getLoan(), renewalContext);
  }

  private <T> Result<T> rescheduleDueDateNotices(Loan loan, T mapTo) {
    return rescheduleLoanNotices(loan, mapTo, DUE_DATE, loan.getDueDate());
  }

  private <T> Result<T> rescheduleLoanNotices(Loan loan, T mapTo, NoticeEventType eventType,
    ZonedDateTime eventTime) {

    log.info("rescheduleLoanNotices:: rescheduling loan notices for event type {}, loan {}",
      () -> eventType, loan::getId);
    TriggeringEvent triggeringEvent = TriggeringEvent.from(eventType);

    if (!loan.isClosed()) {
      log.info("rescheduleLoanNotices:: deleting existing notices and rescheduling for loan {}", loan.getId());
      scheduledNoticesRepository.deleteByLoanIdAndTriggeringEvent(loan.getId(), triggeringEvent)
        .thenAccept(r -> r.next(v -> scheduleLoanNotices(loan, eventType, eventTime)));
    } else {
      log.info("rescheduleLoanNotices:: loan {} is closed, skipping reschedule", loan.getId());
    }

    return succeeded(mapTo);
  }
}
