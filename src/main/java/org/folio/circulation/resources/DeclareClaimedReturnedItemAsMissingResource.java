package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.notes.NoteCreator;
import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.folio.circulation.domain.validation.NotInItemStatusValidator;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.ChangeItemStatusService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.PubSubPublishingService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class DeclareClaimedReturnedItemAsMissingResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public DeclareClaimedReturnedItemAsMissingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/loans/:id/declare-claimed-returned-item-as-missing", router)
      .create(this::declareClaimedReturnedItemAsMissing);
  }

  private void declareClaimedReturnedItemAsMissing(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final EventPublisher eventPublisher = new EventPublisher(new PubSubPublishingService(context));

    createRequest(routingContext)
      .after(request -> processDeclareClaimedReturnedItemAsMissing(routingContext, request))
      .thenCompose(r -> r.after(eventPublisher::publishMarkedAsMissingLoanEvent))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<Loan>> processDeclareClaimedReturnedItemAsMissing(
    RoutingContext routingContext, ChangeItemStatusRequest request) {

    log.debug("processDeclareClaimedReturnedItemAsMissing:: parameters request: {}", () -> request);
    final Clients clients = Clients.create(new WebContext(routingContext), client);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final ChangeItemStatusService changeItemStatusService = new ChangeItemStatusService(
      loanRepository, new StoreLoanAndItem(loanRepository, itemRepository));

    return changeItemStatusService.getOpenLoan(request)
      .thenApply(NotInItemStatusValidator::refuseWhenItemIsNotClaimedReturned)
      .thenApply(r -> declareLoanMissing(r, request))
      .thenCompose(changeItemStatusService::updateLoanAndItem)
      .thenCompose(r -> r.after(loan -> createNote(clients, loan)));
  }

  private Result<Loan> declareLoanMissing(Result<Loan> loanResult, ChangeItemStatusRequest request) {
    log.debug("declareLoanMissing:: parameters request: {}", () -> request);

    return loanResult.map(loan -> loan.markItemMissing(request.getComment()));
  }

  private Result<ChangeItemStatusRequest> createRequest(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();
    log.debug("createRequest:: parameters loanId: {}, body: {}", () -> loanId, () -> body);

    final ChangeItemStatusRequest request = ChangeItemStatusRequest.from(loanId, body);
    if (request.getComment() == null) {
      log.error("createRequest:: comment is a required field");
      return failed(singleValidationError("Comment is a required field",
              "comment", null));
    }

    return succeeded(request);
  }

  private CompletableFuture<Result<Loan>> createNote(Clients clients, Loan loan) {
    log.debug("createNote:: parameters loan: {}", () -> loan);
    final NotesRepository notesRepository = NotesRepository.createUsing(clients);
    final NoteCreator creator = new NoteCreator(notesRepository);

    return creator.createGeneralUserNote(loan.getUserId(), "Claimed returned item marked missing")
      .thenCompose(r -> r.after(note -> completedFuture(succeeded(loan))));
  }
}
