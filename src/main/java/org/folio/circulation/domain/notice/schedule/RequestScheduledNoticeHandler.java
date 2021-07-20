package org.folio.circulation.domain.notice.schedule;

import static java.util.Collections.singletonList;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createRequestNoticeContext;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.HOLD_EXPIRATION;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;

public class RequestScheduledNoticeHandler extends ScheduledNoticeHandler {
  private final RequestRepository requestRepository;

  public RequestScheduledNoticeHandler(Clients clients) {
    super(clients);
    this.requestRepository = RequestRepository.using(clients, true);
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(ScheduledNoticeContext context) {
    return ofAsync(() -> context)
      .thenCompose(r -> r.after(this::fetchTemplate))
      .thenCompose(r -> r.after(this::fetchRequest))
      .thenCompose(r -> r.after(this::fetchPatronNoticePolicyId));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchRequest(ScheduledNoticeContext context) {
    return requestRepository.getById(context.getNotice().getRequestId())
      .thenApply(mapResult(context::withRequest))
      .thenApply(this::failWhenReferencedEntityWasNotFound);
  }

  @Override
  protected Result<ScheduledNoticeContext> failWhenReferencedEntityWasNotFound(
    Result<ScheduledNoticeContext> contextResult)  {

    return contextResult
      .map(ScheduledNoticeContext::getRequest)
      .failWhen(
        request -> succeeded(request.getRequester() == null),
        request -> new RecordNotFoundFailure("user", request.getUserId()))
      .failWhen(
        request -> succeeded(request.getItem() == null || request.getItem().isNotFound()),
        request -> new RecordNotFoundFailure("item", request.getItemId()))
      .next(context -> contextResult);
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchPatronNoticePolicyId(
    ScheduledNoticeContext context) {

    if (isNoticeIrrelevant(context)) {
      return ofAsync(() -> context);
    }

    return patronNoticePolicyRepository.lookupPolicyId(context.getRequest())
      .thenApply(mapResult(CirculationRuleMatch::getPolicyId))
      .thenApply(mapResult(context::withPatronNoticePolicyId));
  }

  @Override
  protected boolean isNoticeIrrelevant(ScheduledNoticeContext context) {
    Request request = context.getRequest();
    ScheduledNotice notice = context.getNotice();

    boolean isHoldExpirationNotice = HOLD_EXPIRATION.equals(notice.getTriggeringEvent());
    boolean isUponAtNotice = UPON_AT.equals(notice.getConfiguration().getTiming());

    return isHoldExpirationNotice &&
      ((!isUponAtNotice && request.isClosed()) || (isUponAtNotice && request.isClosedExceptPickupExpired()));
  }

  @Override
  protected JsonObject buildNoticeContextJson(ScheduledNoticeContext context) {
    return createRequestNoticeContext(context.getRequest());
  }

  @Override
  protected CompletableFuture<Result<ScheduledNotice>> updateNotice(ScheduledNoticeContext context) {
    Request request = context.getRequest();
    ScheduledNotice notice = context.getNotice();

    if (isUponAtNoticeForOpenRequest(context)) {
      return ofAsync(() -> notice);
    }

    if (request.isClosed() || !notice.getConfiguration().isRecurring() || isNoticeIrrelevant(context)) {
      return deleteNoticeAsIrrelevant(notice);
    }

    ScheduledNotice nextRecurringNotice = updateNoticeNextRunTime(notice);

    return nextRecurringNoticeIsNotRelevant(nextRecurringNotice, request)
      ? deleteNoticeAsIrrelevant(notice)
      : scheduledNoticesRepository.update(nextRecurringNotice);
  }

  @Override
  protected NoticeLogContext buildNoticeLogContext(ScheduledNoticeContext context) {
    ScheduledNotice notice = context.getNotice();
    Request request = context.getRequest();

    NoticeLogContextItem logContextItem = NoticeLogContextItem.from(request)
      .withTemplateId(notice.getConfiguration().getTemplateId())
      .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation())
      .withNoticePolicyId(context.getPatronNoticePolicyId());

    return new NoticeLogContext()
      .withUser(request.getRequester())
      .withRequestId(request.getId())
      .withItems(singletonList(logContextItem));
  }

  @Override
  protected boolean shouldNotSendNotice(ScheduledNoticeContext context) {
    return super.shouldNotSendNotice(context) || isUponAtNoticeForOpenRequest(context);
  }

  private static boolean isUponAtNoticeForOpenRequest(ScheduledNoticeContext context) {
    return context.getNotice().getConfiguration().getTiming() == UPON_AT &&
      context.getRequest().isOpen();
  }

  private static ScheduledNotice updateNoticeNextRunTime(ScheduledNotice notice) {
    final DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    DateTime recurringNoticeNextRunTime = notice.getNextRunTime()
      .plus(noticeConfig.getRecurringPeriod().timePeriod());

    if (recurringNoticeNextRunTime.isBefore(systemTime)) {
      recurringNoticeNextRunTime =
        systemTime.plus(noticeConfig.getRecurringPeriod().timePeriod());
    }

    return notice.withNextRunTime(recurringNoticeNextRunTime);
  }

  private static boolean nextRecurringNoticeIsNotRelevant(ScheduledNotice notice, Request request) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return noticeConfig.isRecurring() &&
      noticeConfig.getTiming() == NoticeTiming.BEFORE &&
      nextRunTimeIsAfterRequestExpiration(notice, request);
  }

  private static boolean nextRunTimeIsAfterRequestExpiration(ScheduledNotice notice, Request request) {
    DateTime nextRunTime = notice.getNextRunTime();
    DateTime requestExpirationDate = request.getRequestExpirationDate();
    DateTime holdShelfExpirationDate = request.getHoldShelfExpirationDate();

    return requestExpirationDate != null && nextRunTime.isAfter(requestExpirationDate) ||
      holdShelfExpirationDate != null && nextRunTime.isAfter(holdShelfExpirationDate);
  }

}
