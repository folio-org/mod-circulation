package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createMultiLoanNoticeContext;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.AsyncCoordinationUtil.allResultsOf;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.ScheduledPatronNoticeService;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeHandler.ScheduledNoticeContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;

public class GroupedLoanScheduledNoticeHandler {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final LoanScheduledNoticeHandler loanScheduledNoticeHandler;
  private final ScheduledPatronNoticeService patronNoticeService;

  public GroupedLoanScheduledNoticeHandler(Clients clients,
    LoanRepository loanRepository, ZonedDateTime systemTime) {

    this.loanScheduledNoticeHandler = new LoanScheduledNoticeHandler(clients,
      loanRepository, systemTime);

    this.patronNoticeService = new ScheduledPatronNoticeService(clients);
  }

  public CompletableFuture<Result<List<List<ScheduledNotice>>>> handleNotices(
    List<List<ScheduledNotice>> noticeGroups) {

    log.info("Start processing {} group(s) of scheduled notices ({} notices total)",
      noticeGroups.size(), noticeGroups.stream().mapToInt(List::size).sum());

    return allOf(noticeGroups, this::handleNoticeGroup);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleNoticeGroup(
    List<ScheduledNotice> notices) {

    //TODO: user and template are the same for all notices in the group, so they can be fetched only once
    return allResultsOf(notices, this::fetchData)
      .thenCompose(this::discardDataFetchingFailures)
      .thenCompose(r -> r.after(this::sendGroupedNotice))
      .thenCompose(r -> r.after(this::updateGroupedNotice))
      .thenCompose(r -> handleResult(r, notices))
      .exceptionally(t -> handleException(t, notices));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchData(ScheduledNotice notice) {
    return ofAsync(() -> new ScheduledNoticeContext(notice))
      .thenCompose(r -> r.after(loanScheduledNoticeHandler::fetchData))
      .thenCompose(r -> handleDataCollectionFailure(r, notice))
      .thenApply(r -> r.mapFailure(f -> loanScheduledNoticeHandler.publishErrorEvent(f, notice)));
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> handleDataCollectionFailure(
    Result<ScheduledNoticeContext> result, ScheduledNotice notice) {

    if (result.failed()) {
      HttpFailure cause = result.cause();
      log.error("Failed to collect data for scheduled notice: {}.\n{}", cause, notice);

      return loanScheduledNoticeHandler.deleteNotice(notice, cause.toString())
        .thenApply(r -> r.next(n -> result));
    }

    return completedFuture(result);
  }

  private CompletableFuture<Result<List<ScheduledNoticeContext>>> discardDataFetchingFailures(
    List<Result<ScheduledNoticeContext>> results) {

    var failedResults = results.stream()
      .filter(Result::failed)
      .collect(toList());

    if (!failedResults.isEmpty()) {
      log.error("Failed to collect data for {} out of {} scheduled notices",
        failedResults.size(), results.size());

      results.removeAll(failedResults);
    }

    return completedFuture(results)
      .thenApply(Result::combineAll);
  }

  private CompletableFuture<Result<List<ScheduledNoticeContext>>> sendGroupedNotice(
    List<ScheduledNoticeContext> contexts) {

    if (contexts.isEmpty()) {
      log.warn("No notices left in the group to process, skipping the group");
      return completedFuture(succeeded(contexts));
    }

    List<ScheduledNoticeContext> relevantContexts = contexts.stream()
      .filter(not(loanScheduledNoticeHandler::isNoticeIrrelevant))
      .collect(toList());

    if (relevantContexts.isEmpty()) {
      log.warn("No relevant notices in the group, skipping the group");
      return completedFuture(succeeded(contexts));
    }

    //All the notices have the same properties so we can get any of them
    ScheduledNoticeContext contextSample = relevantContexts.get(0);
    User user = contextSample.getLoan().getUser();

    List<Loan> loans = relevantContexts.stream()
      .map(ScheduledNoticeContext::getLoan)
      .collect(toList());

    log.info("Attempting to send a grouped notice for {} scheduled notices",
      relevantContexts.size());

    return patronNoticeService.sendNotice(
      contextSample.getNotice().getConfiguration(),
      user.getId(),
      createMultiLoanNoticeContext(user, loans),
      buildNoticeLogContext(relevantContexts, user))
      .thenApply(mapResult(v -> contexts));
  }

  private static NoticeLogContext buildNoticeLogContext(List<ScheduledNoticeContext> contexts,
    User user) {

    List<NoticeLogContextItem> items = contexts.stream()
      .map(LoanScheduledNoticeHandler::buildNoticeLogContextItem)
      .collect(toList());

    return new NoticeLogContext()
      .withUser(user)
      .withItems(items);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> updateGroupedNotice(
    List<ScheduledNoticeContext> contexts) {

    return allOf(contexts, loanScheduledNoticeHandler::updateNotice);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> handleResult(
    Result<List<ScheduledNotice>> result, List<ScheduledNotice> notices) {

    if (result.succeeded()) {
      log.info("Group of {} scheduled notices was processed successfully", notices.size());
      return completedFuture(result);
    }

    HttpFailure failure = result.cause();
    log.error("Failed to process group of {} scheduled notices: {}", notices.size(), failure);

    return ofAsync(() -> notices);
  }

  private Result<List<ScheduledNotice>> handleException(Throwable throwable,
    List<ScheduledNotice> notices) {

    log.error("An exception was thrown while processing a group of {} scheduled notices: {}",
      notices.size(), throwable.getLocalizedMessage());

    return succeeded(notices);
  }

}
