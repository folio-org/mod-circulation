package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.notes.NoteCreator;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.LostItemFeeChargingService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class DeclareLostResource extends Resource {
  protected boolean isClaimedReturned;

  public DeclareLostResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    router.post("/circulation/loans/:id/declare-item-lost").handler(this::declareLost);
  }

  private void declareLost(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    final Clients clients = Clients.create(context, client);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);
    final LostItemFeeChargingService lostItemFeeService = new LostItemFeeChargingService(clients);
    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    validateDeclaredLostRequest(routingContext)
      .after(request -> loanRepository.getById(request.getLoanId())
      .thenApply(LoanValidator::refuseWhenLoanIsClosed)
      .thenApply(this::refuseWhenItemIsAlreadyDeclaredLost)
      .thenApply(this::setIsClaimedReturned)
      .thenApply(loan -> declareItemLost(loan, request))
      .thenCompose(r -> r.after(loan -> createNote(clients, loan, isClaimedReturned)))
      .thenCompose(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage))
      .thenCompose(r -> r.after(loan -> lostItemFeeService
        .chargeLostItemFees(loan, request, context.getUserId()))))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDeclaredLostEvent))
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private Result<Loan> declareItemLost(Result<Loan> loanResult,
    DeclareItemLostRequest request) {

    return loanResult.next(loan -> Result.of(() -> loan.declareItemLost(
      Objects.toString(request.getComment(), ""),
      request.getDeclaredLostDateTime())));
  }

  private Result<DeclareItemLostRequest> validateDeclaredLostRequest(
    RoutingContext routingContext) {

    String loanId = routingContext.request().getParam("id");
    return DeclareItemLostRequest.from(routingContext.getBodyAsJson(), loanId);
  }

  private Result<Loan> refuseWhenItemIsAlreadyDeclaredLost(Result<Loan> loanResult) {
    return loanResult.failWhen(
      loan -> Result.succeeded(loan.getItem().isDeclaredLost()),
      loan -> singleValidationError("The item is already declared lost",
        "itemId", loan.getItemId()));
  }

  protected  Result<Loan> setIsClaimedReturned(Result<Loan> loanResult) {
    loanResult.map(loan -> isClaimedReturned = loan.isClaimedReturned());
    return loanResult;
  }

  protected CompletableFuture<Result<Loan>> createNote(Clients clients, Loan loan, boolean isClaimedReturned) {
    final NotesRepository notesRepository = NotesRepository.createUsing(clients);
    final NoteCreator creator = new NoteCreator(notesRepository);

    if (isClaimedReturned) {
      return creator.createNote(loan.getUserId(), "Claimed returned item marked lost")
        .thenCompose(r -> r.after(note -> completedFuture(succeeded(loan))));
    }
    else {
      return completedFuture(succeeded(loan));
    }
  }
}
