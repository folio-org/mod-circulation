package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.StoreLoan;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.representations.ChangeDueDateRequest;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ChangeDueDateResource extends Resource {
  public static final String DUE_DATE = "dueDate";

  public ChangeDueDateResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/loans/:id/change-due-date", router)
      .create(this::changeDueDate);
  }

  private void changeDueDate(RoutingContext routingContext) {
    createChangeDueDateRequest(routingContext)
      .after(request -> processChangeDueDateReturned(request, routingContext))
      .thenApply(NoContentResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<Loan>> processChangeDueDateReturned(
    final ChangeDueDateRequest request, RoutingContext routingContext) {

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final StoreLoan storeLoan = new StoreLoan(loanRepository);

    return succeeded(request)
      .after(req -> loanRepository.getById(req.getLoanId()))
      .thenApply(LoanValidator::refuseWhenLoanIsClosed)
      .thenApply(LoanValidator::refuseWhenItemIsDeclaredLost)
      .thenApply(LoanValidator::refuseWhenItemIsClaimedReturned)
      .thenApply(loan -> makeLoanChangeDueDateReturned(loan, request))
      .thenCompose(r -> r.after(storeLoan::updateLoanInStorage));
  }

  private Result<Loan> makeLoanChangeDueDateReturned(
    Result<Loan> loanResult, ChangeDueDateRequest request) {

    return loanResult.map(loan -> loan
      .changeDueDate(request.getDueDate()));
  }

  private Result<ChangeDueDateRequest> createChangeDueDateRequest(
    RoutingContext routingContext) {

    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();

    if (!body.containsKey(DUE_DATE)) {
      return failed(singleValidationError("Due date is a required field",
        DUE_DATE, null));
    }

    return succeeded(new ChangeDueDateRequest(
      loanId, DateTime.parse(body.getString(DUE_DATE))
    ));
  }
}
