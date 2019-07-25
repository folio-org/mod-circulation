package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.notice.NoticeContextUtil;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class RequestScheduledNoticeHandler {

  public static RequestScheduledNoticeHandler using(Clients clients) {
    return new RequestScheduledNoticeHandler(
      RequestRepository.using(clients, true),
      PatronNoticeService.using(clients),
      ScheduledNoticesRepository.using(clients));
  }

  private RequestRepository requestRepository;
  private PatronNoticeService patronNoticeService;
  private ScheduledNoticesRepository scheduledNoticesRepository;

  private RequestScheduledNoticeHandler(RequestRepository requestRepository,
                                        PatronNoticeService patronNoticeService,
                                        ScheduledNoticesRepository scheduledNoticesRepository) {

    this.requestRepository = requestRepository;
    this.patronNoticeService = patronNoticeService;
    this.scheduledNoticesRepository = scheduledNoticesRepository;
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
      .thenApply(r -> r.map(RequestAndRelatedRecords::new))
      .thenCompose(r -> r.after(records -> sendNotice(records, notice)))
      .thenCompose(r -> r.after(records -> updateNotice(records, notice)));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> sendNotice(
    RequestAndRelatedRecords relatedRecords, ScheduledNotice notice) {
    Request request = relatedRecords.getRequest();

    if (request.isClosed()) {
      return completedFuture(succeeded(relatedRecords));
    }

    JsonObject requestNoticeContext = NoticeContextUtil.createRequestNoticeContext(request);

    return patronNoticeService.acceptScheduledNoticeEvent(
      notice.getConfiguration(), relatedRecords.getUserId(), requestNoticeContext)
      .thenApply(r -> r.map(v -> relatedRecords));
  }

  private CompletableFuture<Result<ScheduledNotice>> updateNotice(
    RequestAndRelatedRecords relatedRecords, ScheduledNotice notice) {

    final DateTime systemTime = DateTime.now(DateTimeZone.UTC);

    Request request = relatedRecords.getRequest();
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    if (request.isClosed() || !noticeConfig.isRecurring()) {
      return scheduledNoticesRepository.delete(notice);
    }

    DateTime recurringNoticeNextRunTime = notice.getNextRunTime()
      .plus(noticeConfig.getRecurringPeriod().timePeriod());
    if (recurringNoticeNextRunTime.isBefore(systemTime)) {
      recurringNoticeNextRunTime =
        systemTime.plus(noticeConfig.getRecurringPeriod().timePeriod());
    }
    ScheduledNotice nextRecurringNotice = notice.withNextRunTime(recurringNoticeNextRunTime);

    if (nextRecurringNoticeIsNotRelevant(nextRecurringNotice, request)) {
      return scheduledNoticesRepository.delete(notice);
    }

    return scheduledNoticesRepository.update(nextRecurringNotice);
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
