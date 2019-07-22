package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.NoticeEventType.HOLD_EXPIRATION;
import static org.folio.circulation.domain.notice.NoticeEventType.REQUEST_EXPIRATION;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.support.Result.succeeded;

import java.util.UUID;

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
    Request request = relatedRecords.getRequest();

    noticePolicyRepository.lookupPolicy(request)
      .thenAccept(r -> r.next(policy -> scheduleRequestNoticesBasedOnPolicy(request, policy)));

    return succeeded(relatedRecords);
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
    if (cfg.getTiming() == UPON_AT) {
      return expirationTime;
    }
    return expirationTime.minus(cfg.getTimingPeriod().timePeriod());
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

    if (type == REQUEST_EXPIRATION && request.getRequestExpirationDate() != null) {
      return true;
    }
    return type == HOLD_EXPIRATION && request.getHoldShelfExpirationDate() != null;

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
}
