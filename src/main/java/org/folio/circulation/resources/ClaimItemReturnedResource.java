package org.folio.circulation.resources;

import static org.folio.circulation.domain.ClaimItemReturnedRequest.ITEM_CLAIMED_RETURNED_DATE;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ClaimItemReturnedRequest;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.services.ChangeItemStatusService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ClaimItemReturnedResource extends Resource {
  public ClaimItemReturnedResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/loans/:id/claim-item-returned", router)
      .create(this::claimItemReturned);
  }

  private void claimItemReturned(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    createRequest(routingContext)
      .after(request -> processClaimItemReturned(routingContext, request))
      .thenCompose(r -> r.after(eventPublisher::publishItemClaimedReturnedEvent))
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<Loan>> processClaimItemReturned(
    RoutingContext routingContext, ClaimItemReturnedRequest request) {

    final Clients clients = Clients.create(new WebContext(routingContext), client);
    final ChangeItemStatusService changeItemStatusService =
      new ChangeItemStatusService(clients);

    return changeItemStatusService.getOpenLoan(request)
      .thenApply(loan -> declareLoanClaimedReturned(loan, request))
      .thenCompose(changeItemStatusService::updateLoanAndItem);
  }

  private Result<Loan> declareLoanClaimedReturned(Result<Loan> loanResult, ClaimItemReturnedRequest request) {
    return loanResult.map(loan -> loan
      .claimItemReturned(request.getComment(), request.getItemClaimedReturnedDateTime()));
  }

  private Result<ClaimItemReturnedRequest> createRequest(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();
    final ClaimItemReturnedRequest request = ClaimItemReturnedRequest.from(loanId, body);

    if (request.getItemClaimedReturnedDateTime() == null) {
      return failed(singleValidationError("Item claimed returned date is a required field",
        ITEM_CLAIMED_RETURNED_DATE, null));
    }

    return succeeded(ClaimItemReturnedRequest.from(loanId, body));
  }
}
