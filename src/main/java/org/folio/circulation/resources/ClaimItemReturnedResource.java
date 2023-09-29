package org.folio.circulation.resources;

import static org.folio.circulation.domain.ClaimItemReturnedRequest.ITEM_CLAIMED_RETURNED_DATE;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.ClaimItemReturnedRequest;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.ChangeItemStatusService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ClaimItemReturnedResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<Loan>> processClaimItemReturned(
    RoutingContext routingContext, ClaimItemReturnedRequest request) {

    log.debug("processClaimItemReturned:: parameters request: {}", () -> request);
    final Clients clients = Clients.create(new WebContext(routingContext), client);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final ChangeItemStatusService changeItemStatusService =
      new ChangeItemStatusService(loanRepository,
        new StoreLoanAndItem(loanRepository, itemRepository));

    return changeItemStatusService.getOpenLoan(request)
      .thenApply(loan -> declareLoanClaimedReturned(loan, request))
      .thenCompose(changeItemStatusService::updateLoanAndItem);
  }

  private Result<Loan> declareLoanClaimedReturned(Result<Loan> loanResult,
    ClaimItemReturnedRequest request) {

    log.debug("declareLoanClaimedReturned:: parameters request: {}", () -> request);

    return loanResult.map(loan -> loan
      .claimItemReturned(request.getComment(), request.getItemClaimedReturnedDateTime()));
  }

  private Result<ClaimItemReturnedRequest> createRequest(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();
    final ClaimItemReturnedRequest request = ClaimItemReturnedRequest.from(loanId, body);
    log.debug("createRequest:: parameters request: {}, loanId: {}, body: {}",
      () -> request, () -> loanId, () -> body);

    if (request.getItemClaimedReturnedDateTime() == null) {
      log.error("createRequest:: Item claimed returned date is a required field");
      return failed(singleValidationError("Item claimed returned date is a required field",
        ITEM_CLAIMED_RETURNED_DATE, null));
    }

    return succeeded(ClaimItemReturnedRequest.from(loanId, body));
  }
}
