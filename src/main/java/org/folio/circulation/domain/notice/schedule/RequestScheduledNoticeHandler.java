package org.folio.circulation.domain.notice.schedule;

import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createRequestNoticeContext;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.HOLD_EXPIRATION;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;

public abstract class RequestScheduledNoticeHandler extends ScheduledNoticeHandler {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  protected final RequestRepository requestRepository;

  protected RequestScheduledNoticeHandler(Clients clients,
    LoanRepository loanRepository, RequestRepository requestRepository) {

    super(clients, loanRepository);
    this.requestRepository = requestRepository;
  }

  @Override
  protected boolean isNoticeIrrelevant(ScheduledNoticeContext context) {
    log.debug("isNoticeIrrelevant:: checking if notice {} is irrelevant for request {}",
      context.getNotice().getId(), context.getRequest() != null ? context.getRequest().getId() : "null");
    return isNoticeNotRelevantYet(context) || isNoticeNoLongerRelevant(context);
  }

  @Override
  protected JsonObject buildNoticeContextJson(ScheduledNoticeContext context) {
    log.debug("buildNoticeContextJson:: building notice context JSON for request {}",
      context.getRequest() != null ? context.getRequest().getId() : "null");
    return createRequestNoticeContext(context.getRequest());
  }

  @Override
  protected CompletableFuture<Result<ScheduledNotice>> updateNotice(ScheduledNoticeContext context) {
    log.debug("updateNotice:: updating scheduled notice {} for request {}",
      context.getNotice().getId(), context.getRequest() != null ? context.getRequest().getId() : "null");
    Request request = context.getRequest();
    ScheduledNotice notice = context.getNotice();
    boolean isNoticeNonRecurring = !notice.getConfiguration().isRecurring();

    if (isNoticeNotRelevantYet(context)) {
      log.debug("updateNotice:: notice {} is not relevant yet, returning as-is", context.getNotice().getId());
      return ofAsync(() -> notice);
    }

    if (request.isClosed() || isNoticeNonRecurring || isNoticeNoLongerRelevant(context)) {
      log.info("updateNotice:: deleting notice {} as irrelevant (closed={}, non-recurring={}, no longer relevant={})",
        notice.getId(), request.isClosed(), isNoticeNonRecurring, isNoticeNoLongerRelevant(context));
      return deleteNoticeAsIrrelevant(notice);
    }

    ScheduledNotice nextRecurringNotice = updateNoticeNextRunTime(notice);

    if (nextRecurringNoticeIsNotRelevant(nextRecurringNotice, request)) {
      log.info("updateNotice:: next recurring notice {} is not relevant, deleting", notice.getId());
      return deleteNoticeAsIrrelevant(notice);
    } else {
      log.info("updateNotice:: updating recurring notice {} with next run time {}",
        nextRecurringNotice.getId(), nextRecurringNotice.getNextRunTime());
      return scheduledNoticesRepository.update(nextRecurringNotice);
    }
  }

  @Override
  protected NoticeLogContext buildNoticeLogContext(ScheduledNoticeContext context) {
    log.debug("buildNoticeLogContext:: building notice log context for request {}",
      context.getRequest() != null ? context.getRequest().getId() : "null");
    return new NoticeLogContext()
      .withUser(context.getRequest().getRequester())
      .withRequestId(context.getRequest().getId())
      .withItems(singletonList(buildNoticeLogContextItem(context)));
  }

  @Override
  protected NoticeLogContextItem buildNoticeLogContextItem(ScheduledNoticeContext context) {
    log.debug("buildNoticeLogContextItem:: building notice log context item for request {}",
      context.getRequest() != null ? context.getRequest().getId() : "null");
    ScheduledNotice notice = context.getNotice();
    Request request = context.getRequest();

    return NoticeLogContextItem.from(request)
      .withTemplateId(notice.getConfiguration().getTemplateId())
      .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation())
      .withNoticePolicyId(context.getPatronNoticePolicyId());
  }

  private static boolean isNoticeNotRelevantYet(ScheduledNoticeContext context) {
    Request request = context.getRequest();
    ScheduledNotice notice = context.getNotice();

    return notice.getConfiguration().getTiming() == UPON_AT && request.isOpen() &&
      !(notice.getTriggeringEvent() == HOLD_EXPIRATION && request.isNotYetFilled());
  }

  private static boolean isNoticeNoLongerRelevant(ScheduledNoticeContext context) {
    return context.getNotice().getTriggeringEvent() == HOLD_EXPIRATION &&
      isHoldExpirationNoticeIrrelevant(context);
  }

  private static boolean isHoldExpirationNoticeIrrelevant(ScheduledNoticeContext context) {
    Request request = context.getRequest();

    return context.getNotice().getConfiguration().getTiming() == UPON_AT
      ? request.isClosed() && !request.isPickupExpired() || request.isNotYetFilled()
      : request.isClosed();
  }

  private static ScheduledNotice updateNoticeNextRunTime(ScheduledNotice notice) {
    final ZonedDateTime systemTime = ClockUtil.getZonedDateTime();
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    ZonedDateTime recurringNoticeNextRunTime = noticeConfig
      .getRecurringPeriod().plusDate(notice.getNextRunTime());

    if (isBeforeMillis(recurringNoticeNextRunTime, systemTime)) {
      recurringNoticeNextRunTime = noticeConfig
        .getRecurringPeriod().plusDate(systemTime);
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
    ZonedDateTime nextRunTime = notice.getNextRunTime();
    ZonedDateTime requestExpirationDate = request.getRequestExpirationDate();
    ZonedDateTime holdShelfExpirationDate = request.getHoldShelfExpirationDate();

    return requestExpirationDate != null && isAfterMillis(nextRunTime, requestExpirationDate) ||
      holdShelfExpirationDate != null && isAfterMillis(nextRunTime, holdShelfExpirationDate);
  }

  protected Result<ScheduledNoticeContext> failWhenRequestHasNoUser(ScheduledNoticeContext context) {
    log.debug("failWhenRequestHasNoUser:: validating user for request {}",
      context.getRequest() != null ? context.getRequest().getId() : "null");
    return failWhenUserIsMissing(context, context.getRequest())
      .map(v -> context);
  }

  protected Result<ScheduledNoticeContext> failWhenRequestHasNoItem(ScheduledNoticeContext context) {
    log.debug("failWhenRequestHasNoItem:: validating item for request {}",
      context.getRequest() != null ? context.getRequest().getId() : "null");
    return failWhenItemIsMissing(context, context.getRequest())
      .map(v -> context);
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchRequestRelatedRecords(
    ScheduledNoticeContext context) {

    log.debug("fetchRequestRelatedRecords:: fetching related records for request {}",
      context.getRequest() != null ? context.getRequest().getId() : context.getNotice().getRequestId());

    // request is expected to have been fetched before it reaches this handler
    if (context.getRequest() == null) {
      log.warn("fetchRequestRelatedRecords:: request is null for notice {}", context.getNotice().getId());
      return completedFuture(failed(
        new RecordNotFoundFailure("request", context.getNotice().getRequestId())));
    }

    return requestRepository.fetchRelatedRecords(context.getRequest())
      .thenCompose(r -> r.after(this::fetchLatestPatronInfoAddedComment))
      .thenApply(mapResult(context::withRequest))
      .thenApply(r -> r.next(this::failWhenRequestHasNoUser));
  }

  private CompletableFuture<Result<Request>> fetchLatestPatronInfoAddedComment(Request request) {
    log.debug("fetchLatestPatronInfoAddedComment:: fetching patron info comment for request {}",
      request != null ? request.getId() : "null");
    if (request.getLoan() != null) {
      return loanRepository.fetchLatestPatronInfoAddedComment(request.getLoan())
        .thenApply(mapResult(request::withLoan));
    }
    return ofAsync(request);
  }

}
