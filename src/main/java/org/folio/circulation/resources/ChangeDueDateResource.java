package org.folio.circulation.resources;

import static org.folio.circulation.domain.representations.ChangeDueDateRequest.DUE_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeService;
import org.folio.circulation.domain.representations.ChangeDueDateRequest;
import org.folio.circulation.domain.validation.ItemStatusValidator;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ChangeDueDateResource extends Resource {
  public ChangeDueDateResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/loans/:id/change-due-date", router)
      .create(this::changeDueDate);
  }

  private void changeDueDate(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    createChangeDueDateRequest(routingContext)
      .after(r -> processChangeDueDate(r, routingContext))
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> processChangeDueDate(
    final ChangeDueDateRequest request, RoutingContext routingContext) {

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);

    final DueDateScheduledNoticeService scheduledNoticeService
      = DueDateScheduledNoticeService.using(clients);

    final ItemStatusValidator itemStatusValidator = new ItemStatusValidator(
        ChangeDueDateResource::errorWhenInIncorrectStatus);

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients);

    return succeeded(request)
      .after(r -> loanRepository.getById(r.getLoanId()))
      .thenApply(LoanValidator::refuseWhenLoanIsClosed)
      .thenApply(this::toLoanAndRelatedRecords)
      .thenApply(itemStatusValidator::refuseWhenItemStatusDoesNotAllowDueDateChange)
      .thenApply(r -> changeDueDate(r, request))
      .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDueDateChangedEvent))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenCompose(r -> r.after(loanNoticeSender::sendManualDueDateChangeNotice));
  }

  private Result<LoanAndRelatedRecords> changeDueDate(Result<LoanAndRelatedRecords> loanResult,
      ChangeDueDateRequest request) {

    return loanResult.map(l -> changeDueDate(l, request.getDueDate()));
  }

  private LoanAndRelatedRecords changeDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
    DateTime dueDate) {

    loanAndRelatedRecords.getLoan().changeDueDate(dueDate);
    return loanAndRelatedRecords;
  }

  private Result<ChangeDueDateRequest> createChangeDueDateRequest(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();

    if (!body.containsKey(DUE_DATE)) {
      return failed(singleValidationError(
        "A new due date is required in order to change the due date", DUE_DATE, null));
    }

    return Result.of(() -> new ChangeDueDateRequest(loanId, getDateTimeProperty(body, DUE_DATE)));
  }

  private Result<LoanAndRelatedRecords> toLoanAndRelatedRecords(Result<Loan> loanResult) {
    return loanResult.next(loan -> succeeded(new LoanAndRelatedRecords(loan)));
  }

  private static ValidationErrorFailure errorWhenInIncorrectStatus(Item item) {
    String message = String.format("item is %s", item.getStatusName());

    return singleValidationError(message, ITEM_ID, item.getItemId());
  }
}
