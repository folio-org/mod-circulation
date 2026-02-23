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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.ItemRelatedRecord;
import org.folio.circulation.domain.UserRelatedRecord;
import org.folio.circulation.domain.notice.ScheduledPatronNoticeService;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public abstract class ScheduledNoticeHandler {
  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  protected final ScheduledNoticesRepository scheduledNoticesRepository;
  protected final LoanRepository loanRepository;
  protected final AccountRepository accountRepository;
  protected final PatronNoticePolicyRepository patronNoticePolicyRepository;
  protected final CollectionResourceClient templateNoticesClient;
  private final ScheduledPatronNoticeService patronNoticeService;
  private final EventPublisher eventPublisher;

  protected ScheduledNoticeHandler(Clients clients, LoanRepository loanRepository) {
    this.scheduledNoticesRepository = ScheduledNoticesRepository.using(clients);
    this.loanRepository = loanRepository;
    this.accountRepository = new AccountRepository(clients);
    this.patronNoticePolicyRepository = new PatronNoticePolicyRepository(clients);
    this.templateNoticesClient = clients.noticeTemplatesClient();
    this.patronNoticeService = new ScheduledPatronNoticeService(clients);
    this.eventPublisher = new EventPublisher(clients);
  }

  public CompletableFuture<Result<List<ScheduledNotice>>> handleContexts(
    Collection<ScheduledNoticeContext> contexts) {

    log.debug("handleContexts:: handling {} notice contexts", contexts != null ? contexts.size() : 0);

    return allOf(contexts, this::handleContext);
  }

  public CompletableFuture<Result<List<ScheduledNotice>>> handleNotices(
    Collection<ScheduledNotice> scheduledNotices) {

    log.debug("handleNotices:: handling {} scheduled notices", scheduledNotices != null ? scheduledNotices.size() : 0);

    return allOf(scheduledNotices, this::handleNotice);
  }

  private CompletableFuture<Result<ScheduledNotice>> handleNotice(ScheduledNotice notice) {
    log.debug("handleNotice:: processing scheduled notice {}", notice != null ? notice.getId() : "null");
    return handleContext(new ScheduledNoticeContext(notice));
  }

  protected CompletableFuture<Result<ScheduledNotice>> handleContext(ScheduledNoticeContext context) {
    log.debug("handleContext:: handling context for notice {}", context.getNotice().getId());
    final ScheduledNotice notice = context.getNotice();

    return ofAsync(context)
      .thenCompose(r -> r.after(this::fetchNoticeData))
      .thenCompose(r -> r.after(this::sendNotice))
      .thenCompose(r -> r.after(this::updateNotice))
      .thenCompose(r -> handleResult(r, notice))
      .exceptionally(t -> handleException(t, notice));
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchNoticeData(
    ScheduledNoticeContext context) {

    log.debug("fetchNoticeData:: fetching notice data for notice {}", context.getNotice().getId());

    return ofAsync(() -> context)
      .thenCompose(r -> r.after(this::fetchData))
      .thenApply(result -> handleNoticeContextData(context, result));
  }

  private Result<ScheduledNoticeContext> handleNoticeContextData(ScheduledNoticeContext context,
    Result<ScheduledNoticeContext> result) {

    log.debug("handleNoticeContextData:: handling notice context data for notice {}", context.getNotice().getId());
    if (result.succeeded()) {
      return result;
    }

    if (result.cause() instanceof RecordNotFoundFailure failure &&
        Objects.nonNull(failure.getScheduledNoticeContext()) &&
        hasClosedLoanWithNullUser(failure.getScheduledNoticeContext())) {
      log.info("handleNoticeContextData:: ignoring record not found for closed loan with null user, notice {}", context.getNotice().getId());
      return succeeded(context);
    }

    return result.mapFailure(failure -> publishErrorEvent(failure, context.getNotice()));
  }

  private boolean hasClosedLoanWithNullUser(ScheduledNoticeContext context) {
    log.debug("hasClosedLoanWithNullUser:: checking if loan is closed with null user");
    var loan = context.getLoan();
    return loan != null && loan.isClosed() && loan.getUser() == null;
  }

  protected abstract CompletableFuture<Result<ScheduledNoticeContext>> fetchData(
    ScheduledNoticeContext context);

  protected abstract CompletableFuture<Result<ScheduledNotice>> updateNotice(
    ScheduledNoticeContext context);

  protected abstract boolean isNoticeIrrelevant(ScheduledNoticeContext context);

  protected abstract NoticeLogContext buildNoticeLogContext(ScheduledNoticeContext context);

  protected abstract NoticeLogContextItem buildNoticeLogContextItem(ScheduledNoticeContext context);

  protected abstract JsonObject buildNoticeContextJson(ScheduledNoticeContext context);

  protected Result<ScheduledNoticeContext> publishErrorEvent(HttpFailure failure,
    ScheduledNotice notice) {

    log.warn("publishErrorEvent:: publishing error event for notice {}, failure: {}",
      notice != null ? notice.getId() : "null", failure);

    eventPublisher.publishNoticeErrorLogEvent(NoticeLogContext.from(notice), failure);

    return failed(failure);
  }

  protected CompletableFuture<Result<ScheduledNotice>> deleteNotice(ScheduledNotice notice,
    String reason) {

    log.info("deleteNotice:: deleting scheduled notice {}. Reason: {}", notice.getId(), reason);

    return scheduledNoticesRepository.delete(notice);
  }

  protected CompletableFuture<Result<ScheduledNotice>> deleteNoticeAsIrrelevant(
    ScheduledNotice notice) {

    log.info("deleteNoticeAsIrrelevant:: deleting notice {} as irrelevant", notice != null ? notice.getId() : "null");

    return deleteNotice(notice, "notice is no longer relevant");
  }

  protected Result<ScheduledNoticeContext> failWhenLoanIsIncomplete(
    ScheduledNoticeContext context) {

    log.debug("failWhenLoanIsIncomplete:: validating loan completeness for loan {}",
      context.getLoan() != null ? context.getLoan().getId() : "null");

    return failWhenUserIsMissing(context, context.getLoan())
      .next(r -> failWhenItemIsMissing(context, context.getLoan()))
      .map(v -> context);
  }

  protected Result<Void> failWhenUserIsMissing(ScheduledNoticeContext context, UserRelatedRecord userRelatedRecord) {
    if (userRelatedRecord.getUser() == null) {
      log.warn("failWhenUserIsMissing:: user not found for user ID {}", userRelatedRecord.getUserId());
    }
    return userRelatedRecord.getUser() == null
      ? failed(new RecordNotFoundFailure("user", userRelatedRecord.getUserId(), context))
      : succeeded(null);
  }

  protected Result<Void> failWhenItemIsMissing(ScheduledNoticeContext context, ItemRelatedRecord itemRelatedRecord) {
    if (itemRelatedRecord.getItem() == null || itemRelatedRecord.getItem().isNotFound()) {
      log.warn("failWhenItemIsMissing:: item not found for item ID {}", itemRelatedRecord.getItemId());
    }
    return itemRelatedRecord.getItem() == null || itemRelatedRecord.getItem().isNotFound()
      ? failed(new RecordNotFoundFailure("item", itemRelatedRecord.getItemId(), context))
      : succeeded(null);
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> sendNotice(
    ScheduledNoticeContext context) {

    log.debug("sendNotice:: sending notice for scheduled notice {}", context.getNotice().getId());
    if (isNoticeIrrelevant(context)) {
      log.debug("sendNotice:: notice is irrelevant, skipping send");
      return ofAsync(() -> context);
    }

    return patronNoticeService.sendNotice(
      context.getNotice().getConfiguration(),
      context.getNotice().getRecipientUserId(),
      buildNoticeContextJson(context),
      buildNoticeLogContext(context))
      .thenApply(r -> {
        if (r.succeeded()) {
          log.info("sendNotice:: successfully sent notice {}", context.getNotice().getId());
        }
        return r.map(v -> context);
      });
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchPatronNoticePolicyIdForLoan(
    ScheduledNoticeContext context) {

    log.debug("fetchPatronNoticePolicyIdForLoan:: fetching patron notice policy ID for loan {}",
      context.getLoan() != null ? context.getLoan().getId() : "null");

    return fetchPatronNoticePolicyId(context, context.getLoan());
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchPatronNoticePolicyIdForRequest(
    ScheduledNoticeContext context) {

    log.debug("fetchPatronNoticePolicyIdForRequest:: fetching patron notice policy ID for request {}",
      context.getRequest() != null ? context.getRequest().getId() : "null");

    return fetchPatronNoticePolicyId(context, context.getRequest());
  }

  protected <T extends UserRelatedRecord & ItemRelatedRecord>
  CompletableFuture<Result<ScheduledNoticeContext>> fetchPatronNoticePolicyId(
    ScheduledNoticeContext context, T userAndItemRelatedRecord) {

    log.debug("fetchPatronNoticePolicyId:: fetching policy ID for notice {}", context.getNotice().getId());
    if (isNoticeIrrelevant(context)) {
      log.debug("fetchPatronNoticePolicyId:: notice is irrelevant, skipping policy fetch");
      return ofAsync(() -> context);
    }

    return patronNoticePolicyRepository.lookupPolicyId(userAndItemRelatedRecord)
      .thenApply(mapResult(CirculationRuleMatch::getPolicyId))
      .thenApply(mapResult(context::withPatronNoticePolicyId));
  }

  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchTemplate(
    ScheduledNoticeContext context) {

    String templateId = context.getNotice().getConfiguration().getTemplateId();
    log.debug("fetchTemplate:: fetching template {} for notice {}", templateId, context.getNotice().getId());

    var responseInterpreter = new ResponseInterpreter<ScheduledNoticeContext>()
      .on(404, failed(new RecordNotFoundFailure("template", templateId)))
      .on(200, succeeded(context))
      .otherwise(forwardOnFailure());

    // no need to save the template, we only fetch it in order to fail if it does not exist
    return templateNoticesClient.get(templateId)
      .thenApply(responseInterpreter::flatMap);
  }

  protected CompletableFuture<Result<ScheduledNotice>> handleResult(Result<ScheduledNotice> result,
    ScheduledNotice notice) {

    if (result.succeeded()) {
      log.info("handleResult:: finished processing scheduled notice {}", notice.getId());
      return completedFuture(result);
    }

    HttpFailure failure = result.cause();
    log.error("handleResult:: processing scheduled notice {} failed: {}", notice.getId(), failure);

    return deleteNotice(notice, failure.toString());
  }

  protected Result<ScheduledNotice> handleException(Throwable throwable, ScheduledNotice notice) {
    log.error("handleException:: exception thrown while processing scheduled notice {}: {}", notice.getId(), throwable.getMessage());

    return succeeded(notice);
  }

}
