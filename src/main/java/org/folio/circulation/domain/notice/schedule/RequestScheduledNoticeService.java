package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.REQUEST_EXPIRATION;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.TITLE_LEVEL_REQUEST_EXPIRATION;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeConfigurationBuilder;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class RequestScheduledNoticeService {
  public static RequestScheduledNoticeService using(Clients clients) {
    return new RequestScheduledNoticeService(
      ScheduledNoticesRepository.using(clients),
      new PatronNoticePolicyRepository(clients));
  }

  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private RequestScheduledNoticeService(
    ScheduledNoticesRepository scheduledNoticesRepository,
    PatronNoticePolicyRepository noticePolicyRepository) {
    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.noticePolicyRepository = noticePolicyRepository;
  }


  public Result<RequestAndRelatedRecords> scheduleRequestNotices(RequestAndRelatedRecords relatedRecords) {
    Request request = relatedRecords.getRequest();
    if (request.isClosed()) {
      return succeeded(relatedRecords);
    }

    if (request.hasItemId()) {
      scheduleNoticesForRequestWithItemId(request);
    } else {
      scheduleNoticesForRequestWithoutItemId(request);
    }

    return succeeded(relatedRecords);
  }

  public Result<RequestAndRelatedRecords> rescheduleRequestNotices(RequestAndRelatedRecords relatedRecords) {
    Request request = relatedRecords.getRequest();
    scheduledNoticesRepository.deleteByRequestId(request.getId())
      .thenAccept(r -> r.next(resp -> scheduleNoticesForRequestWithItemId(request)));

    return succeeded(relatedRecords);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> rescheduleRequestNotices(LoanAndRelatedRecords relatedRecords) {
    Request request = relatedRecords.getClosedFilledRequest();
    if (request == null) {
      return completedFuture(succeeded(relatedRecords));
    }
    scheduledNoticesRepository.deleteByRequestId(request.getId())
      .thenAccept(r -> r.next(resp -> scheduleNoticesForRequestWithItemId(request)));

    return completedFuture(succeeded(relatedRecords));
  }

  public Result<CheckInContext> rescheduleRequestNotices(CheckInContext context) {
    Optional.ofNullable(context.getHighestPriorityFulfillableRequest())
      .ifPresent(this::rescheduleRequestNotices);

    return succeeded(context);
  }

  private void rescheduleRequestNotices(Request request) {
    scheduledNoticesRepository.deleteByRequestId(request.getId())
      .thenAccept(r -> r.next(resp -> scheduleNoticesForRequestWithItemId(request)));
  }

  private Result<PatronNoticePolicy> scheduleRequestNoticesBasedOnPolicy(
    Request request, PatronNoticePolicy noticePolicy) {

    noticePolicy.getNoticeConfigurations()
      .stream()
      .map(cfg -> createRequestScheduledNoticeBasedOnNoticeConfig(cfg, request))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .forEach(scheduledNoticesRepository::create);

    return succeeded(noticePolicy);
  }

  private Optional<ScheduledNotice> createRequestScheduledNoticeBasedOnNoticeConfig(
    NoticeConfiguration cfg, Request request) {
    NoticeEventType eventType = cfg.getNoticeEventType();

    if (eventType == NoticeEventType.REQUEST_EXPIRATION) {
      return createRequestExpirationScheduledNotice(request, cfg, REQUEST_EXPIRATION);
    } else if (eventType == NoticeEventType.HOLD_EXPIRATION) {
      return createHoldExpirationScheduledNotice(request, cfg);
    } else {
      return Optional.empty();
    }
  }

  private Optional<ScheduledNotice> createRequestExpirationScheduledNotice(
    Request request, NoticeConfiguration cfg, TriggeringEvent triggeringEvent) {

    return Optional.ofNullable(request.getRequestExpirationDate())
      .map(expirationDate -> determineNextRunTime(expirationDate, cfg))
      .map(nextRunTime -> createScheduledNotice(request, nextRunTime, cfg, triggeringEvent));
  }

  private Optional<ScheduledNotice> createHoldExpirationScheduledNotice(
    Request request, NoticeConfiguration cfg) {

    return Optional.ofNullable(request.getHoldShelfExpirationDate())
      .map(expirationDate -> determineNextRunTime(expirationDate, cfg))
      .map(nextRunTime -> createScheduledNotice(request, nextRunTime, cfg, TriggeringEvent.HOLD_EXPIRATION));
  }

  private ZonedDateTime determineNextRunTime(ZonedDateTime expirationDate, NoticeConfiguration cfg) {
    return cfg.getTiming() == UPON_AT
      ? expirationDate
      : cfg.getTimingPeriod().minusDate(expirationDate);
  }

  private ScheduledNotice createScheduledNotice(Request request,
    ZonedDateTime nextRunTime,
    NoticeConfiguration cfg,
    TriggeringEvent triggeringEvent) {
    return new ScheduledNoticeBuilder()
      .setId(UUID.randomUUID().toString())
      .setRequestId(request.getId())
      .setRecipientUserId(request.getUserId())
      .setTriggeringEvent(triggeringEvent)
      .setNextRunTime(nextRunTime)
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

  private Result<Request> scheduleNoticesForRequestWithItemId(Request request) {
    if (!request.isClosed()) {
      noticePolicyRepository.lookupPolicy(request)
        .thenApply(r -> r.next(policy -> scheduleRequestNoticesBasedOnPolicy(request, policy)));
    }

    return succeeded(request);
  }

  private Result<Request> scheduleNoticesForRequestWithoutItemId(Request request) {
    if (request.isTitleLevel()) {
      scheduleRequestNoticesBasedOnTlrSettings(request);
    }

    return succeeded(request);
  }

  private Result<TlrSettingsConfiguration> scheduleRequestNoticesBasedOnTlrSettings(Request request) {
    TlrSettingsConfiguration tlrSettingsConfiguration = request.getTlrSettingsConfiguration();
    UUID expirationTemplateId = tlrSettingsConfiguration.getExpirationPatronNoticeTemplateId();
    if (expirationTemplateId != null) {
      NoticeConfiguration noticeConfiguration = new NoticeConfigurationBuilder()
        .setTemplateId(expirationTemplateId.toString())
        .setNoticeFormat(NoticeFormat.EMAIL)
        .setNoticeEventType(NoticeEventType.TITLE_LEVEL_REQUEST_EXPIRATION)
        .setTiming(UPON_AT)
        .setRecurring(false)
        .setSendInRealTime(true)
        .build();

      createRequestExpirationScheduledNotice(request, noticeConfiguration, TITLE_LEVEL_REQUEST_EXPIRATION)
        .map(scheduledNoticesRepository::create);
    } else {
      log.info("ExpirationPatronNoticeTemplateId is not present, scheduled notice will not be created");
    }

    return succeeded(tlrSettingsConfiguration);
  }
}
