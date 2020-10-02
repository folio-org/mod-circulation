package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.notes.NoteCreator;
import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.folio.circulation.domain.validation.NotInItemStatusValidator;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.services.ChangeItemStatusService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class DeclareClaimedReturnedItemAsMissingResource extends Resource {
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

    createRequest(routingContext)
      .after(request -> processDeclareClaimedReturnedItemAsMissing(routingContext, request))
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<Loan>> processDeclareClaimedReturnedItemAsMissing(
    RoutingContext routingContext, ChangeItemStatusRequest request) {

    final Clients clients = Clients.create(routingContext, client);
    final ChangeItemStatusService changeItemStatusService = new ChangeItemStatusService(clients);

    return changeItemStatusService.getOpenLoan(request)
      .thenApply(NotInItemStatusValidator::refuseWhenItemIsNotClaimedReturned)
      .thenApply(r -> declareLoanMissing(r, request))
      .thenCompose(changeItemStatusService::updateLoanAndItem)
      .thenCompose(r -> r.after(loan -> createNote(clients, loan)));
  }

  private Result<Loan> declareLoanMissing(Result<Loan> loanResult, ChangeItemStatusRequest request) {
    return loanResult.map(loan -> loan.markItemMissing(request.getComment()));
  }

  private Result<ChangeItemStatusRequest> createRequest(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();

    final ChangeItemStatusRequest request = ChangeItemStatusRequest.from(loanId, body);
    if (request.getComment() == null) {
      return failed(singleValidationError("Comment is a required field",
              "comment", null));
    }

    return succeeded(request);
  }

  private CompletableFuture<Result<Loan>> createNote(Clients clients, Loan loan) {
    final NotesRepository notesRepository = NotesRepository.createUsing(clients);
    final NoteCreator creator = new NoteCreator(notesRepository);

    return creator.createGeneralUserNote(loan.getUserId(), "Claimed returned item marked missing")
      .thenCompose(r -> r.after(note -> completedFuture(succeeded(loan))));
  }
}
