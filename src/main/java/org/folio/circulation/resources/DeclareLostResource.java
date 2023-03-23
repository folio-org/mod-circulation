package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang.StringUtils.defaultIfBlank;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.notes.NoteCreator;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.LostItemFeeChargingService;
import org.folio.circulation.services.LostItemFeeRefundService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class DeclareLostResource extends Resource {

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
    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    validateDeclaredLostRequest(routingContext)
      .after(request -> declareItemLost(request, clients, context))
      .thenComposeAsync(r -> r.after(loan -> publishEvent(loan, eventPublisher)))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<Loan>> publishEvent(Loan loan, EventPublisher eventPublisher) {
    if (loan.isDeclaredLost()) {
      return eventPublisher.publishDeclaredLostEvent(loan);
    }

    if (loan.isClosed()) {
      return eventPublisher.publishLoanClosedEvent(loan);
    }

    return ofAsync(() -> null);
  }

  private CompletableFuture<Result<Loan>> declareItemLost(DeclareItemLostRequest request,
    Clients clients, WebContext context) {

    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);
    final var lostItemFeeService = new LostItemFeeChargingService(clients, storeLoanAndItem,
      new LostItemFeeRefundService(clients, itemRepository, userRepository, loanRepository));

    return loanRepository.getById(request.getLoanId())
      .thenApply(LoanValidator::refuseWhenLoanIsClosed)
      .thenApply(this::refuseWhenItemIsAlreadyDeclaredLost)
      .thenCompose(r -> r.after(lostItemFeeService::refuseWhenFeeFineOwnerIsNotFound))
      .thenCompose(declareItemLost(request, clients))
      .thenCompose(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage))
      .thenCompose(r -> r.after(loan -> lostItemFeeService
        .chargeLostItemFees(loan, request, context.getUserId())));
  }

  private Function<Result<Loan>, CompletionStage<Result<Loan>>> declareItemLost(
    DeclareItemLostRequest request, Clients clients) {

    return r -> r.after(when(loan -> ofAsync(loan::isClaimedReturned),
      loan -> declareItemLostWhenClaimedReturned(loan, request, clients),
      loan -> declareItemLostWhenNotClaimedReturned(loan, request)));
  }

  private CompletableFuture<Result<Loan>> declareItemLostWhenNotClaimedReturned(
    Loan loan, DeclareItemLostRequest request) {

    return ofAsync(() -> declareItemLost(loan, request));
  }

  private CompletableFuture<Result<Loan>> declareItemLostWhenClaimedReturned(
    Loan loan, DeclareItemLostRequest request, Clients clients) {

    final NotesRepository notesRepository = NotesRepository.createUsing(clients);
    final NoteCreator creator = new NoteCreator(notesRepository);

    return ofAsync(() -> declareItemLost(loan, request))
      .thenCompose(r -> r.after(l -> creator.createGeneralUserNote(loan.getUserId(),
        "Claimed returned item marked declared lost")))
      .thenCompose(r -> r.after(note -> completedFuture(succeeded(loan))));
  }

  private Loan declareItemLost(Loan loan, DeclareItemLostRequest request) {
    return loan.declareItemLost(defaultIfBlank(request.getComment(), ""),
      request.getDeclaredLostDateTime());
  }

  private Result<DeclareItemLostRequest> validateDeclaredLostRequest(
    RoutingContext routingContext) {

    String loanId = routingContext.request().getParam("id");
    return DeclareItemLostRequest.from(routingContext.getBodyAsJson(), loanId);
  }

  private Result<Loan> refuseWhenItemIsAlreadyDeclaredLost(Result<Loan> loanResult) {
    return loanResult.failWhen(
      loan -> succeeded(loan.getItem().isDeclaredLost()),
      loan -> singleValidationError("The item is already declared lost",
        "itemId", loan.getItemId()));
  }
}
