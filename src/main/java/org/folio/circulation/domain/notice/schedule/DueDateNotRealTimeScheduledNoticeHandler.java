package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeHandler.REQUIRED_RECORD_TYPES;
import static org.folio.circulation.support.AsyncCoordinationUtil.allResultsOf;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.domain.notice.TemplateRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class DueDateNotRealTimeScheduledNoticeHandler {

  public static DueDateNotRealTimeScheduledNoticeHandler using(Clients clients, DateTime systemTime) {

    return new DueDateNotRealTimeScheduledNoticeHandler(
      DueDateScheduledNoticeHandler.using(clients, systemTime),
      new LoanRepository(clients),
      new LoanPolicyRepository(clients),
      PatronNoticeService.using(clients),
      TemplateRepository.using(clients));
  }

  private final DueDateScheduledNoticeHandler dueDateScheduledNoticeHandler;
  private final LoanRepository loanRepository;
  private final LoanPolicyRepository loanPolicyRepository;
  private final PatronNoticeService patronNoticeService;
  private TemplateRepository templateRepository;
  private static final String TEMPLATE_RECORD_TYPE = "template";

  public DueDateNotRealTimeScheduledNoticeHandler(
    DueDateScheduledNoticeHandler dueDateScheduledNoticeHandler,
    LoanRepository loanRepository,
    LoanPolicyRepository loanPolicyRepository,
    PatronNoticeService patronNoticeService,
    TemplateRepository templateRepository) {

    this.dueDateScheduledNoticeHandler = dueDateScheduledNoticeHandler;
    this.loanRepository = loanRepository;
    this.loanPolicyRepository = loanPolicyRepository;
    this.patronNoticeService = patronNoticeService;
    this.templateRepository = templateRepository;
  }

  public CompletableFuture<Result<Void>> handleNotices(
    List<List<ScheduledNotice>> noticeGroups) {

    CompletableFuture<Result<Void>> future = completedFuture(succeeded(null));
    for (List<ScheduledNotice> noticeGroup : noticeGroups) {
      future = future.thenCompose(r -> r.after(v -> handleNoticeGroup(noticeGroup)));
    }
    return future.thenApply(mapResult(v -> null));
  }

  private CompletableFuture<Result<Void>> handleNoticeGroup(List<ScheduledNotice> noticeGroup) {
  return allResultsOf(noticeGroup, this::getContext)
      .thenCompose(this::handleFailures)
      .thenCompose(r -> r.after(this::sendGroupedNotice))
      .thenCompose(r -> r.after(this::updateGroupedNotice))
      .thenApply(mapResult(p -> null));
  }

  private CompletableFuture<Result<List<Pair<ScheduledNotice, LoanAndRelatedRecords>>>> handleFailures(
    List<Result<Pair<ScheduledNotice, LoanAndRelatedRecords>>> results) {
    results.removeIf(r -> dueDateScheduledNoticeHandler.failedToFindRecordOfType(r, REQUIRED_RECORD_TYPES));
    return CompletableFuture.completedFuture(results)
      .thenApply(Result::combineAll);
  }

  private CompletableFuture<Result<Pair<ScheduledNotice, LoanAndRelatedRecords>>> getContext(ScheduledNotice notice) {

    String templateId = notice.getConfiguration().getTemplateId();

    return templateRepository.findById(templateId)
      .thenApply(r -> r.next(response -> failIfTemplateNotFound(response, templateId)))
      .thenCompose(r -> r.after(i -> loanRepository.getById(notice.getLoanId())))
      .thenCompose(r -> dueDateScheduledNoticeHandler.deleteNoticeIfLoanIsMissingOrIncomplete(r, notice))
      .thenApply(mapResult(LoanAndRelatedRecords::new))
      .thenCompose(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenApply(mapResult(relatedRecords -> Pair.of(notice, relatedRecords)));
  }

  private Result<Response> failIfTemplateNotFound(Response response, String templateId) {
    if (response.getStatusCode() == 400) {
      return failed(new RecordNotFoundFailure(TEMPLATE_RECORD_TYPE, templateId));
    } else {
      return succeeded(response);
    }
  }

  private CompletableFuture<Result<List<Pair<ScheduledNotice, LoanAndRelatedRecords>>>> sendGroupedNotice(
    List<Pair<ScheduledNotice, LoanAndRelatedRecords>> noticeGroup) {

    List<Pair<ScheduledNotice, LoanAndRelatedRecords>> relevantNotices =
      noticeGroup.stream().filter(this::noticeIsRelevant).collect(Collectors.toList());
    if (relevantNotices.isEmpty()) {
      return completedFuture(succeeded(noticeGroup));
    }

    List<JsonObject> loanContexts = relevantNotices.stream()
      .map(Pair::getRight)
      .map(LoanAndRelatedRecords::getLoan)
      .map(TemplateContextUtil::createLoanNoticeContextWithoutUser)
      .collect(Collectors.toList());

    //All the notices have the same properties so we can get any of them
    ScheduledNotice scheduledNotice = relevantNotices.get(0).getLeft();
    LoanAndRelatedRecords noticeRelatedRecords = relevantNotices.get(0).getRight();

    User user = noticeRelatedRecords.getLoan().getUser();
    JsonObject noticeContext = new JsonObject()
      .put("user", TemplateContextUtil.createUserContext(user))
      .put("loans", new JsonArray(loanContexts));

    return patronNoticeService.acceptScheduledNoticeEvent(
      scheduledNotice.getConfiguration(), user.getId(), noticeContext)
      .thenApply(mapResult(v -> noticeGroup));
  }

  private boolean noticeIsRelevant(Pair<ScheduledNotice, LoanAndRelatedRecords> noticeWithContext) {
    ScheduledNotice notice = noticeWithContext.getLeft();
    Loan loan = noticeWithContext.getRight().getLoan();

    return !dueDateScheduledNoticeHandler.noticeIsNotRelevant(notice, loan);
  }

  private CompletableFuture<Result<List<Pair<ScheduledNotice, LoanAndRelatedRecords>>>> updateGroupedNotice(
    List<Pair<ScheduledNotice, LoanAndRelatedRecords>> noticeGroup) {

    CompletableFuture<Result<ScheduledNotice>> future = completedFuture(succeeded(null));
    for (Pair<ScheduledNotice, LoanAndRelatedRecords> notice : noticeGroup) {
      future = future.thenCompose(r -> r.after(v ->
        dueDateScheduledNoticeHandler.updateNotice(notice.getRight(), notice.getLeft())));
    }
    return future.thenApply(mapResult(v -> noticeGroup));
  }

}
