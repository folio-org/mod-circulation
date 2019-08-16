package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.NoticeEventType.HOLD_EXPIRATION;
import static org.folio.circulation.domain.notice.NoticeEventType.REQUEST_EXPIRATION;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

public class RequestScheduledNoticeService {
  public static RequestScheduledNoticeService using(Clients clients) {
    return new RequestScheduledNoticeService(
      ScheduledNoticesRepository.using(clients),
      new PatronNoticePolicyRepository(clients));
  }

  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;

  private RequestScheduledNoticeService(
    ScheduledNoticesRepository scheduledNoticesRepository,
    PatronNoticePolicyRepository noticePolicyRepository) {
    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.noticePolicyRepository = noticePolicyRepository;
  }


  public Result<RequestAndRelatedRecords> scheduleRequestNotices(RequestAndRelatedRecords relatedRecords) {
    scheduleRequestNotices(relatedRecords.getRequest());
    return succeeded(relatedRecords);
  }

  public Result<RequestAndRelatedRecords> rescheduleRequestNotices(RequestAndRelatedRecords relatedRecords) {
    Request request = relatedRecords.getRequest();
    scheduledNoticesRepository.deleteByRequestId(request.getId())
      .thenAccept(r -> r.next(resp -> scheduleRequestNotices(request)));

    return succeeded(relatedRecords);
  }

  public Result<CheckInProcessRecords> rescheduleRequestNotices(CheckInProcessRecords records) {
    Optional.ofNullable(records.getHighestPriorityFulfillableRequest())
      .ifPresent(this::rescheduleRequestNotices);

    return succeeded(records);
  }

  private void rescheduleRequestNotices(Request request) {
    scheduledNoticesRepository.deleteByRequestId(request.getId())
      .thenAccept(r -> r.next(resp -> scheduleRequestNotices(request)));
  }

  private Result<PatronNoticePolicy> scheduleRequestNoticesBasedOnPolicy(
    Request request, PatronNoticePolicy noticePolicy) {

    noticePolicy.getNoticeConfigurations()
      .stream()
      .filter(cfg -> requiresNoticeScheduling(cfg, request))
      .map(cfg -> createRequestScheduledNotice(cfg, request))
      .forEach(scheduledNoticesRepository::create);

    return succeeded(noticePolicy);
  }

  private DateTime determineNextRunTime(NoticeConfiguration cfg, Request request) {
    DateTime expirationTime = getExpirationTime(cfg, request);

    return cfg.getTiming() == UPON_AT ? expirationTime :
      expirationTime.minus(cfg.getTimingPeriod().timePeriod());
  }

  private DateTime getExpirationTime(NoticeConfiguration cfg, Request request) {
    switch (cfg.getNoticeEventType()) {
      case REQUEST_EXPIRATION:
        return request.getRequestExpirationDate();
      case HOLD_EXPIRATION:
        return request.getHoldShelfExpirationDate();
      default:
        throw new IllegalStateException();
    }
  }

  private boolean requiresNoticeScheduling(NoticeConfiguration cfg, Request request) {
    NoticeEventType type = cfg.getNoticeEventType();

    return type == REQUEST_EXPIRATION && request.getRequestExpirationDate() != null
      || type == HOLD_EXPIRATION && request.getHoldShelfExpirationDate() != null;
  }

  private ScheduledNotice createRequestScheduledNotice(NoticeConfiguration cfg, Request request) {
    return new ScheduledNoticeBuilder()
      .setId(UUID.randomUUID().toString())
      .setRequestId(request.getId())
      .setTriggeringEvent(cfg.getNoticeEventType().getRepresentation())
      .setNextRunTime(determineNextRunTime(cfg, request))
      .setNoticeConfig(createScheduledNoticeConfig(cfg))
      .build();
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

  private Result<Request> scheduleRequestNotices(Request request) {
    noticePolicyRepository.lookupPolicy(request)
      .thenApply(r -> r.next(policy -> scheduleRequestNoticesBasedOnPolicy(request, policy)));

    return succeeded(request);
  }
}
