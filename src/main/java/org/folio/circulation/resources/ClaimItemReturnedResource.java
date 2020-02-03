package org.folio.circulation.resources;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.domain.LoanAction.CLAIMED_RETURNED;
import static org.folio.circulation.domain.representations.ClaimItemReturnedProperties.ITEM_CLAIMED_RETURNED_DATE;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.ClaimItemReturnedRequest;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.validation.LoanValidators;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
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
    validateRequest(routingContext)
      .after(this::processClaimReturned)
      .thenApply(NoContentResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<Loan>> processClaimReturned(WebContext context) {
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = ItemRepository.fetchItemOnlyInstance(clients);
    final StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);

    final ClaimItemReturnedRequest request = ClaimItemReturnedRequest.from(context);

    return succeeded(request)
      .after(req -> loanRepository.getById(req.getLoanId()))
      .thenApply(LoanValidators::refuseWhenLoanIsClosed)
      .thenApply(loan -> makeLoanAndItemClaimedReturned(loan, request))
      .thenCompose(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage));
  }

  private Result<Loan> makeLoanAndItemClaimedReturned(
    Result<Loan> loanResult, ClaimItemReturnedRequest request) {

    return loanResult.next(loan -> {
      loan.changeAction(CLAIMED_RETURNED);
      if (StringUtils.isNotBlank(request.getComment())) {
        loan.changeActionComment(request.getComment());
      }

      loan.changeItemStatusForItemAndLoan(ItemStatus.CLAIMED_RETURNED);
      loan.changeClaimedReturned(request.getLoanClaimedReturned());

      return succeeded(loan);
    });
  }

  private Result<WebContext> validateRequest(RoutingContext routingContext) {
    return succeeded(routingContext)
      .failWhen(ctx -> succeeded(ctx.getBodyAsJson()
          .getString(ITEM_CLAIMED_RETURNED_DATE) == null),
        request -> singleValidationError("Item claimed returned date is a required field",
          ITEM_CLAIMED_RETURNED_DATE, null))
      .map(WebContext::new)
      .failWhen(context -> succeeded(isBlank(context.getUserId())),
        context -> singleValidationError("No okapi user id provided", USER_ID, null));
  }
}
