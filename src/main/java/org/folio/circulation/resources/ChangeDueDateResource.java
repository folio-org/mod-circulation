package org.folio.circulation.resources;

import static org.folio.circulation.domain.representations.ChangeDueDateRequest.DUE_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.representations.ChangeDueDateRequest;
import org.folio.circulation.domain.validation.ItemStatusValidator;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ChangeDueDateResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> processChangeDueDate(
    final ChangeDueDateRequest request, RoutingContext routingContext) {

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);

    final LoanRepository loanRepository = new LoanRepository(clients);

    final LoanScheduledNoticeService scheduledNoticeService
      = LoanScheduledNoticeService.using(clients);

    final ItemStatusValidator itemStatusValidator = new ItemStatusValidator(
        ChangeDueDateResource::errorWhenInIncorrectStatus);

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients);
    log.info("starting change due date process for loan " + request.getLoanId());
    return succeeded(request)
      .after(r -> getExistingLoan(loanRepository, r))
      .thenApply(LoanValidator::refuseWhenLoanIsClosed)
      .thenApply(this::toLoanAndRelatedRecords)
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenApply(itemStatusValidator::refuseWhenItemStatusDoesNotAllowDueDateChange)
      .thenApply(r -> changeDueDate(r, request))
      .thenApply(r -> r.map(this::unsetDueDateChangedByRecallIfNoOpenRecallsInQueue))
      .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDueDateChangedEvent))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenCompose(r -> r.after(loanNoticeSender::sendManualDueDateChangeNotice));
  }
  
  private LoanAndRelatedRecords unsetDueDateChangedByRecallIfNoOpenRecallsInQueue(
      LoanAndRelatedRecords loanAndRelatedRecords) {
    
    RequestQueue queue = loanAndRelatedRecords.getRequestQueue();
    Loan loan = loanAndRelatedRecords.getLoan();
    log.info("Loan " + loan.getId() + " prior to flag check: " + loan.asJson().toString());
    if (loan.wasDueDateChangedByRecall() && !queue.hasOpenRecalls()) {
      log.info("Loan " + loan.getId() + " registers as having due date change flag set to true and no open recalls in queue.");
      return loanAndRelatedRecords.withLoan(loan.unsetDueDateChangedByRecall());
    } else {
      log.info("Loan " + loan.getId() + " registers as either not having due date change flag set to true or as having open recalls in queue.");
      return loanAndRelatedRecords;
    }
  }

  CompletableFuture<Result<Loan>> getExistingLoan(LoanRepository loanRepository, ChangeDueDateRequest changeDueDateRequest) {
    return loanRepository.getById(changeDueDateRequest.getLoanId())
      .thenApplyAsync(r -> r.map(exitingLoan -> exitingLoan.setPreviousDueDate(exitingLoan.getDueDate())));
  }

  private Result<LoanAndRelatedRecords> changeDueDate(Result<LoanAndRelatedRecords> loanResult,
      ChangeDueDateRequest request) {

    return loanResult.map(l -> changeDueDate(l, request.getDueDate()));
  }

  private LoanAndRelatedRecords changeDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
    ZonedDateTime dueDate) {

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
