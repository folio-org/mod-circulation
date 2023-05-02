package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createGroupedNoticeContext;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.AsyncCoordinationUtil.allResultsOf;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.ScheduledPatronNoticeService;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public abstract class GroupedScheduledNoticeHandler {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ScheduledNoticeHandler singleNoticeHandler;
  private final ScheduledPatronNoticeService patronNoticeService;
  private final String groupToken;

  protected GroupedScheduledNoticeHandler(Clients clients,
    ScheduledNoticeHandler singleNoticeHandler, String groupToken) {

    this.singleNoticeHandler = singleNoticeHandler;
    this.patronNoticeService = new ScheduledPatronNoticeService(clients);
    this.groupToken = groupToken;
  }

  public CompletableFuture<Result<List<List<ScheduledNotice>>>> handleNotices(
    List<List<ScheduledNotice>> noticeGroups) {

    log.info("handleNotices:: processing {} group(s) of scheduled notices ({} notices total)",
      noticeGroups.size(), noticeGroups.stream().mapToInt(List::size).sum());

    return allOf(noticeGroups, this::handleNoticeGroup);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleNoticeGroup(
    List<ScheduledNotice> notices) {

    log.debug("handleNoticeGroup:: processing group of {} scheduled notices", notices.size());

    //TODO: user and template are the same for all notices in the group, so they can be fetched only once
    return allResultsOf(notices, this::buildContext)
      .thenCompose(this::discardContextBuildingFailures)
      .thenCompose(r -> r.after(this::sendGroupedNotice))
      .thenCompose(r -> r.after(this::updateGroupedNotice))
      .thenCompose(r -> handleResult(r, notices))
      .exceptionally(t -> handleException(t, notices));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> buildContext(ScheduledNotice notice) {
    log.debug("buildContext:: building context for notice {}", notice);

    return ofAsync(() -> new ScheduledNoticeContext(notice))
      .thenCompose(r -> r.after(singleNoticeHandler::fetchData))
      .thenApply(r -> r.map(this::buildNoticeContextJson))
      .thenApply(r -> r.map(this::buildNoticeLogContextItem))
      .thenCompose(r -> handleContextBuildingFailure(r, notice))
      .thenApply(r -> r.mapFailure(f -> singleNoticeHandler.publishErrorEvent(f, notice)));
  }

  private ScheduledNoticeContext buildNoticeContextJson(ScheduledNoticeContext context) {
    return context.withNoticeContext(buildNoticeContext(context));
  }

  protected abstract JsonObject buildNoticeContext(ScheduledNoticeContext context);

  private ScheduledNoticeContext buildNoticeLogContextItem(ScheduledNoticeContext context) {
    return context.withNoticeLogContextItem(
      singleNoticeHandler.buildNoticeLogContextItem(context));
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> handleContextBuildingFailure(
    Result<ScheduledNoticeContext> result, ScheduledNotice notice) {

    if (result.failed()) {
      HttpFailure cause = result.cause();
      log.warn("handleContextBuildingFailure:: failed to build context: {}. Reason: {}", notice, cause);

      return singleNoticeHandler.deleteNotice(notice, cause.toString())
        .thenApply(r -> r.next(n -> result));
    }

    log.info("handleContextBuildingFailure:: context built for notice {}", notice.getId());

    return completedFuture(result);
  }

  private CompletableFuture<Result<List<ScheduledNoticeContext>>> discardContextBuildingFailures(
    List<Result<ScheduledNoticeContext>> results) {

    var failedResults = results.stream()
      .filter(Result::failed)
      .collect(toList());

    if (!failedResults.isEmpty()) {
      log.error("discardContextBuildingFailures:: failed to build context for {} out of {} notices",
        failedResults.size(), results.size());
      results.removeAll(failedResults);
    } else {
      log.info("discardContextBuildingFailures:: all contexts were built successfully");
    }

    return completedFuture(results)
      .thenApply(Result::combineAll);
  }

  private CompletableFuture<Result<List<ScheduledNoticeContext>>> sendGroupedNotice(
    List<ScheduledNoticeContext> contexts) {

    if (contexts.isEmpty()) {
      log.info("sendGroupedNotice:: no notices left in the group to process, skipping the group");
      return completedFuture(succeeded(contexts));
    }

    List<ScheduledNoticeContext> relevantContexts = contexts.stream()
      .filter(not(singleNoticeHandler::isNoticeIrrelevant))
      .collect(toList());

    if (relevantContexts.isEmpty()) {
      log.info("sendGroupedNotice:: no relevant notices in the group, skipping the group");
      return completedFuture(succeeded(contexts));
    }

    //All the notices have the same properties, so we can get any of them
    ScheduledNoticeContext contextSample = relevantContexts.get(0);
    User user = contextSample.getLoan().getUser();

    List<JsonObject> noticeContexts = relevantContexts.stream()
      .map(ScheduledNoticeContext::getNoticeContext)
      .collect(toList());

    log.info("sendGroupedNotice:: attempting to send a grouped notice for {} scheduled notices",
      relevantContexts.size());

    return patronNoticeService.sendNotice(
        contextSample.getNotice().getConfiguration(),
        user.getId(),
        createGroupedNoticeContext(user, groupToken, noticeContexts),
        buildNoticeLogContext(relevantContexts, user))
      .thenApply(mapResult(v -> contexts));
  }

  private static NoticeLogContext buildNoticeLogContext(List<ScheduledNoticeContext> contexts,
    User user) {

    List<NoticeLogContextItem> items = contexts.stream()
      .map(ScheduledNoticeContext::getNoticeLogContextItem)
      .collect(toList());

    return new NoticeLogContext()
      .withUser(user)
      .withItems(items);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> updateGroupedNotice(
    List<ScheduledNoticeContext> contexts) {

    log.debug("updateGroupedNotice:: updating {} notices", contexts.size());

    return allOf(contexts, singleNoticeHandler::updateNotice);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleResult(
    Result<List<ScheduledNotice>> result, List<ScheduledNotice> notices) {

    if (result.succeeded()) {
      log.info("handleResult:: group of {} notices was processed successfully", notices.size());
      return completedFuture(result);
    }

    HttpFailure failure = result.cause();
    log.error("handleResult:: failed to process group of {} notices: {}", notices.size(), failure);

    return ofAsync(() -> notices);
  }

  private Result<List<ScheduledNotice>> handleException(Throwable throwable,
    List<ScheduledNotice> notices) {

    log.error("handleException:: exception was thrown while processing a group of {} notices: {}",
      notices.size(), throwable.getLocalizedMessage());

    return succeeded(notices);
  }

}
