package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;

import org.folio.circulation.domain.notice.ScheduledPatronNoticeService;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.UnableToApplyCircRulesErrorFailure;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;

public abstract class ScheduledNoticeHandler {
  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  protected final ScheduledNoticesRepository scheduledNoticesRepository;
  protected final LoanRepository loanRepository;
  protected final AccountRepository accountRepository;
  protected final PatronNoticePolicyRepository patronNoticePolicyRepository;
  protected final CollectionResourceClient templateNoticesClient;
  protected final ScheduledPatronNoticeService patronNoticeService;
  protected final EventPublisher eventPublisher;

  protected ScheduledNoticeHandler(Clients clients) {
    this.scheduledNoticesRepository = ScheduledNoticesRepository.using(clients);
    this.loanRepository = new LoanRepository(clients);
    this.accountRepository = new AccountRepository(clients);
    this.patronNoticePolicyRepository = new PatronNoticePolicyRepository(clients);
    this.templateNoticesClient = clients.noticeTemplatesClient();
    this.patronNoticeService = new ScheduledPatronNoticeService(clients);
    this.eventPublisher = new EventPublisher(clients.pubSubPublishingService());
  }

  public CompletableFuture<Result<List<ScheduledNotice>>> handleNotices(
    Collection<ScheduledNotice> scheduledNotices) {

    return allOf(scheduledNotices, this::handleNotice);
  }

  private CompletableFuture<Result<ScheduledNotice>> handleNotice(ScheduledNotice notice) {
    log.info("Start processing scheduled notice {}", notice);

    return ofAsync(() -> new ScheduledNoticeContext(notice))
      .thenCompose(r -> r.after(this::fetchNoticeData))
      .thenCompose(r -> r.after(this::sendNotice))
      .thenCompose(r -> r.after(this::updateNotice))
      .thenCompose(r -> handleResult(r, notice))
      .exceptionally(t -> handleException(t, notice));
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchNoticeData(
    ScheduledNoticeContext context) {

    return ofAsync(() -> context)
      .thenCompose(r -> r.after(this::fetchData))
      .thenApply(r -> r.mapFailure(f -> publishErrorEvent(f, context.notice)));
  }

  protected abstract CompletableFuture<Result<ScheduledNoticeContext>> fetchData(
    ScheduledNoticeContext context);

  protected abstract CompletableFuture<Result<ScheduledNotice>> updateNotice(
    ScheduledNoticeContext context);

  protected abstract boolean isNoticeIrrelevant(ScheduledNoticeContext context);

  protected abstract NoticeLogContext buildNoticeLogContext(ScheduledNoticeContext context);

  protected abstract JsonObject buildNoticeContextJson(ScheduledNoticeContext context);

  protected boolean noticeShouldNotBeSent(ScheduledNoticeContext context) {
    return isNoticeIrrelevant(context);
  }

  protected Result<ScheduledNoticeContext> publishErrorEvent(HttpFailure failure,
    ScheduledNotice notice) {

    eventPublisher.publishNoticeErrorLogEvent(NoticeLogContext.from(notice), failure);

    return failed(failure);
  }

  protected CompletableFuture<Result<ScheduledNotice>> deleteNotice(ScheduledNotice notice,
    String reason) {

    log.info("Deleting scheduled notice {}. Reason: {}", notice.getId(), reason);

    return scheduledNoticesRepository.delete(notice);
  }

  protected CompletableFuture<Result<ScheduledNotice>> deleteNoticeAsIrrelevant(
    ScheduledNotice notice) {

    return deleteNotice(notice, "notice is no longer relevant");
  }

  protected Result<ScheduledNoticeContext> failWhenLoanIsIncomplete(
    Result<ScheduledNoticeContext> contextResult)  {

    return contextResult
      .map(ScheduledNoticeContext::getLoan)
      .failWhen(
        loan -> succeeded(loan.getUser() == null),
        loan -> new RecordNotFoundFailure("user", loan.getUserId()))
      .failWhen(
        loan -> succeeded(loan.getItem() == null || loan.getItem().isNotFound()),
        loan -> new RecordNotFoundFailure("item", loan.getItemId()))
      .next(context -> contextResult);
  }

  protected Result<ScheduledNoticeContext> failWhenRequestIsIncomplete(
    Result<ScheduledNoticeContext> contextResult)  {

    return contextResult
      .map(ScheduledNoticeContext::getRequest)
      .failWhen(
        request -> succeeded(request.getRequester() == null),
        request -> new RecordNotFoundFailure("user", request.getRequesterId()))
      .failWhen(
        request -> succeeded(request.getItem() == null || request.getItem().isNotFound()),
        request -> new RecordNotFoundFailure("item", request.getItemId()))
      .next(context -> contextResult);
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> sendNotice(
    ScheduledNoticeContext context) {

    if (noticeShouldNotBeSent(context)) {
      return ofAsync(() -> context);
    }

    return patronNoticeService.sendNotice(
      context.getNotice().getConfiguration(),
      context.getNotice().getRecipientUserId(),
      buildNoticeContextJson(context),
      buildNoticeLogContext(context))
      .thenApply(r -> r.map(v -> context));
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchPatronNoticePolicyId(
    ScheduledNoticeContext context) {

    if (isNoticeIrrelevant(context)) {
      return ofAsync(() -> context);
    }

    return patronNoticePolicyRepository.lookupPolicyId(context.getLoan())
      .thenApply(mapResult(CirculationRuleMatch::getPolicyId))
      .thenApply(mapResult(context::withPatronNoticePolicyId));
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchTemplate(
    ScheduledNoticeContext context) {

    String templateId = context.getNotice().getConfiguration().getTemplateId();

    var responseInterpreter = new ResponseInterpreter<ScheduledNoticeContext>()
      .on(404, failed(new RecordNotFoundFailure("template", templateId)))
      .on(200, succeeded(context))
      .otherwise(forwardOnFailure());

    // no need to save the template, we only fetch it in order to fail if it does not exist
    return templateNoticesClient.get(templateId)
      .thenApply(responseInterpreter::flatMap);
  }

  private CompletableFuture<Result<ScheduledNotice>> handleResult(Result<ScheduledNotice> result,
    ScheduledNotice notice) {

    if (result.succeeded()) {
      log.info("Finished processing scheduled notice {}", notice.getId());
      return completedFuture(result);
    }

    HttpFailure failure = result.cause();
    log.error("Processing scheduled notice {} failed: {}", notice.getId(), failure);

    if (failure instanceof RecordNotFoundFailure || failure instanceof UnableToApplyCircRulesErrorFailure
      || failure instanceof ForwardOnFailure) {
      return deleteNotice(notice, failure.toString());
    }

    return ofAsync(() -> notice);
  }

  private Result<ScheduledNotice> handleException(Throwable throwable, ScheduledNotice notice) {
    deleteNotice(notice, String.format("an exception was thrown while processing scheduled notice %s: %s",
    notice.getId(), throwable.getMessage()));
    return succeeded(notice);
  }

  @With
  @Getter
  @RequiredArgsConstructor
  @AllArgsConstructor
  protected static class ScheduledNoticeContext {
    private final ScheduledNotice notice;
    private Account account;
    private FeeFineAction action;
    private Loan loan;
    private Request request;
    private String patronNoticePolicyId;
    private boolean lostItemFeesForAgedToLostNoticeExist;
  }

}
