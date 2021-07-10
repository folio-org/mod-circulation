package org.folio.circulation.domain.notice.schedule;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.HOLD_EXPIRATION;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;

public class RequestScheduledNoticeHandler {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static RequestScheduledNoticeHandler using(Clients clients) {
    return new RequestScheduledNoticeHandler(
      RequestRepository.using(clients, true),
      PatronNoticeService.using(clients),
      ScheduledNoticesRepository.using(clients),
      new PatronNoticePolicyRepository(clients));
  }

  private RequestRepository requestRepository;
  private PatronNoticeService patronNoticeService;
  private ScheduledNoticesRepository scheduledNoticesRepository;
  private PatronNoticePolicyRepository noticePolicyRepository;

  private RequestScheduledNoticeHandler(RequestRepository requestRepository,
                                        PatronNoticeService patronNoticeService,
                                        ScheduledNoticesRepository scheduledNoticesRepository,
                                        PatronNoticePolicyRepository noticePolicyRepository) {

    this.requestRepository = requestRepository;
    this.patronNoticeService = patronNoticeService;
    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.noticePolicyRepository = noticePolicyRepository;
  }

  public CompletableFuture<Result<Collection<ScheduledNotice>>> handleNotices(Collection<ScheduledNotice> scheduledNotices) {
    CompletableFuture<Result<ScheduledNotice>> future = completedFuture(succeeded(null));
    for (ScheduledNotice scheduledNotice : scheduledNotices) {
      future = future.thenCompose(r -> r.after(v -> handleRequestNotice(scheduledNotice)));
    }
    return future.thenApply(r -> r.map(v -> scheduledNotices));
  }

  private CompletableFuture<Result<ScheduledNotice>> handleRequestNotice(ScheduledNotice notice) {
    return requestRepository.getById(notice.getRequestId())
      .thenCompose(r -> r.after(when(request -> noticeIsIrrelevant(request, notice),
        request -> scheduledNoticesRepository.delete(notice),
        request -> sendAndUpdateNotice(request, notice))));
  }

  private CompletableFuture<Result<Boolean>> noticeIsIrrelevant(
    Request request, ScheduledNotice notice) {

    boolean holdExpirationNotice = HOLD_EXPIRATION.equals(notice.getTriggeringEvent());
    boolean uponAtNotice = UPON_AT.equals(notice.getConfiguration().getTiming());
    boolean closedRequest = request.isClosed();
    boolean closedExceptPickupExpiredRequest = request.isClosedExceptPickupExpired();

    if (holdExpirationNotice &&
      ((!uponAtNotice && closedRequest) || (uponAtNotice && closedExceptPickupExpiredRequest))) {

      log.info(format("Request %s is closed, deleting hold shelf expiration scheduled notice %s",
        request.getId(), notice.getId()));

      return completedFuture(succeeded(true));
    }

    return completedFuture(succeeded(false));
  }

  private CompletableFuture<Result<ScheduledNotice>> sendAndUpdateNotice(Request request,
    ScheduledNotice notice) {

    return completedFuture(succeeded(new RequestAndRelatedRecords(request)))
      .thenCompose(res -> res.after(records -> sendNotice(records, notice)))
      .thenCompose(res -> res.after(records -> updateNotice(records, notice)));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> sendNotice(
    RequestAndRelatedRecords relatedRecords, ScheduledNotice notice) {
    Request request = relatedRecords.getRequest();

    if (notice.getConfiguration().getTiming() == UPON_AT && request.isOpen()) {
      return completedFuture(succeeded(relatedRecords));
    }

    JsonObject requestNoticeContext = TemplateContextUtil.createRequestNoticeContext(request);

    NoticeLogContextItem logContextItem = NoticeLogContextItem.from(request)
      .withTemplateId(notice.getConfiguration().getTemplateId())
      .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation());

    return noticePolicyRepository.lookupPolicyId(request.getItem(), request.getRequester())
      .thenCompose(r -> r.after(policy -> patronNoticeService.acceptScheduledNoticeEvent(
        notice.getConfiguration(), relatedRecords.getUserId(), requestNoticeContext,
        new NoticeLogContext().withUser(request.getRequester())
          .withRequestId(request.getId())
          .withItems(Collections.singletonList(logContextItem.withNoticePolicyId(policy.getPolicyId()))))))
      .thenApply(r -> r.map(v -> relatedRecords));
  }

  private CompletableFuture<Result<ScheduledNotice>> updateNotice(
    RequestAndRelatedRecords relatedRecords, ScheduledNotice notice) {

    Request request = relatedRecords.getRequest();
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    if (noticeConfig.getTiming() == UPON_AT && request.isOpen()) {
      return completedFuture(succeeded(notice));
    }

    if (request.isClosed() || !noticeConfig.isRecurring()) {
      return scheduledNoticesRepository.delete(notice);
    }

    ScheduledNotice nextRecurringNotice = updateNoticeNextRunTime(notice, noticeConfig);
    return nextRecurringNoticeIsNotRelevant(nextRecurringNotice, request) ?
      scheduledNoticesRepository.delete(notice) :
      scheduledNoticesRepository.update(nextRecurringNotice);
  }

  private ScheduledNotice updateNoticeNextRunTime(
    ScheduledNotice notice, ScheduledNoticeConfig noticeConfig) {

    final DateTime systemTime = DateTime.now(DateTimeZone.UTC);

    DateTime recurringNoticeNextRunTime = notice.getNextRunTime()
      .plus(noticeConfig.getRecurringPeriod().timePeriod());
    if (recurringNoticeNextRunTime.isBefore(systemTime)) {
      recurringNoticeNextRunTime =
        systemTime.plus(noticeConfig.getRecurringPeriod().timePeriod());
    }

    return notice.withNextRunTime(recurringNoticeNextRunTime);
  }

  private boolean nextRecurringNoticeIsNotRelevant(
    ScheduledNotice notice, Request request) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return noticeConfig.isRecurring() &&
      noticeConfig.getTiming() == NoticeTiming.BEFORE &&
      nextRunTimeIsAfterRequestExpiration(notice, request);
  }

  private boolean nextRunTimeIsAfterRequestExpiration(ScheduledNotice notice, Request request) {
    DateTime nextRunTime = notice.getNextRunTime();
    DateTime requestExpirationDate = request.getRequestExpirationDate();
    DateTime holdShelfExpirationDate = request.getHoldShelfExpirationDate();

    return requestExpirationDate != null && nextRunTime.isAfter(requestExpirationDate) ||
      holdShelfExpirationDate != null && nextRunTime.isAfter(holdShelfExpirationDate);
  }
}
