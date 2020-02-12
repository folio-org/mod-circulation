package org.folio.circulation.resources;

import static org.folio.circulation.support.ItemRepository.noLocationMaterialTypeAndLoanTypeInstance;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.ClaimItemReturnedRequest;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ClaimItemReturnedResource extends Resource {
  public static final String ITEM_CLAIMED_RETURNED_DATE = "itemClaimedReturnedDateTime";
  public static final String COMMENT = "comment";

  public ClaimItemReturnedResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/loans/:id/claim-item-returned", router)
      .create(this::claimItemReturned);
  }

  private void claimItemReturned(RoutingContext routingContext) {
    createClaimItemReturnedRequest(routingContext)
      .after(request -> processClaimItemReturned(request, routingContext))
      .thenApply(NoContentResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<Loan>> processClaimItemReturned(
    final ClaimItemReturnedRequest request, RoutingContext routingContext) {

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = noLocationMaterialTypeAndLoanTypeInstance(clients);
    final StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);

    return succeeded(request)
      .after(req -> loanRepository.getById(req.getLoanId()))
      .thenApply(LoanValidator::refuseWhenLoanIsClosed)
      .thenApply(loan -> makeLoanAndItemClaimedReturned(loan, request))
      .thenCompose(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage));
  }

  private Result<Loan> makeLoanAndItemClaimedReturned(
    Result<Loan> loanResult, ClaimItemReturnedRequest request) {

    return loanResult.map(loan -> loan
      .claimItemReturned(request.getComment(), request.getItemClaimedReturnedDateTime()));
  }

  private Result<ClaimItemReturnedRequest> createClaimItemReturnedRequest(
    RoutingContext routingContext) {

    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();

    if (!body.containsKey(ITEM_CLAIMED_RETURNED_DATE)) {
      return failed(singleValidationError("Item claimed returned date is a required field",
        ITEM_CLAIMED_RETURNED_DATE, null));
    }

    return succeeded(new ClaimItemReturnedRequest(
      loanId, DateTime.parse(body.getString(ITEM_CLAIMED_RETURNED_DATE)),
      body.getString(COMMENT)));
  }
}
