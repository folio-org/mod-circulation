package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang.StringUtils.defaultIfBlank;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.notes.NoteCreator;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.DeclareLostContext;
import org.folio.circulation.services.DeclareLostItemFeeRefundService;
import org.folio.circulation.services.DeclareLostService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.LostItemFeeChargingService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class DeclareLostResource extends Resource {
  private static final String NO_FEE_FINE_OWNER_FOUND = "No fee/fine owner found for item's permanent location";
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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
    log.debug("publishEvent:: parameters loan: {}", () -> loan);
    if (loan.isDeclaredLost()) {
      log.info("publishEvent:: publish declaredLostEvent");
      return eventPublisher.publishDeclaredLostEvent(loan);
    }

    if (loan.isClosed()) {
      log.info("publishEvent:: publish loanClosedEvent");
      return eventPublisher.publishLoanClosedEvent(loan);
    }

    return ofAsync(() -> null);
  }

  private CompletableFuture<Result<Loan>> declareItemLost(DeclareItemLostRequest request,
    Clients clients, WebContext context) {

    log.debug("declareItemLost:: parameters request: {}", () -> request);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);
    final var lostItemFeeService = new LostItemFeeChargingService(clients, storeLoanAndItem,
      new DeclareLostItemFeeRefundService(clients, itemRepository, userRepository, loanRepository));
    final var declareLostService = new DeclareLostService(new LostItemPolicyRepository(clients),
      LocationRepository.using(clients, new ServicePointRepository(clients)),
      new FeeFineOwnerRepository(clients));

      return loanRepository.getById(request.getLoanId())
        .thenApply(LoanValidator::refuseWhenLoanIsClosed)
        .thenApply(this::refuseWhenItemIsAlreadyDeclaredLost)
        .thenApply(r -> r.map(loan -> new DeclareLostContext(loan, request)))
        .thenCompose(declareLostService::fetchLostItemPolicy)
        .thenCompose(r -> r.after(declareLostService::fetchFeeFineOwner))
        .thenCompose(r -> r.after(this::refuseWhenFeeFineOwnerIsNotFound))
        .thenCompose(declareItemLost(clients))
        .thenCompose(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage))
        .thenCompose(r -> r.after(ctx -> lostItemFeeService
          .chargeLostItemFees(ctx, context.getUserId())));
  }

  private Function<Result<DeclareLostContext>, CompletionStage<Result<DeclareLostContext>>>
  declareItemLost(Clients clients) {

    return r -> r.after(when(ctx -> ofAsync(ctx.getLoan().isClaimedReturned()),
      ctx -> declareItemLostWhenClaimedReturned(ctx, clients),
      this::declareItemLostWhenNotClaimedReturned));
  }

  private CompletableFuture<Result<DeclareLostContext>> declareItemLostWhenNotClaimedReturned(
    DeclareLostContext declareLostContext) {

    return ofAsync(() -> declareItemLost(declareLostContext));
  }

  private CompletableFuture<Result<DeclareLostContext>> declareItemLostWhenClaimedReturned(
    DeclareLostContext declareLostContext, Clients clients) {

    log.debug("declareItemLostWhenClaimedReturned:: parameters loan: {}", declareLostContext::getLoan);
    final NotesRepository notesRepository = NotesRepository.createUsing(clients);
    final NoteCreator creator = new NoteCreator(notesRepository);

    return ofAsync(() -> declareItemLost(declareLostContext))
      .thenCompose(r -> r.after(l -> creator.createGeneralUserNote(
        declareLostContext.getLoan().getUserId(),
        "Claimed returned item marked declared lost")))
      .thenCompose(r -> r.after(note -> completedFuture(succeeded(declareLostContext))));
  }

  private DeclareLostContext declareItemLost(DeclareLostContext declareLostContext) {
    return declareLostContext.withLoan(
      declareLostContext.getLoan().declareItemLost(defaultIfBlank(
        declareLostContext.getRequest().getComment(), ""),
        declareLostContext.getRequest().getDeclaredLostDateTime()));
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

  public CompletableFuture<Result<DeclareLostContext>> refuseWhenFeeFineOwnerIsNotFound(
    DeclareLostContext declareLostContext) {

    return ofAsync(declareLostContext::getFeeFineOwner)
      .thenApply(r -> r.failWhen(
        owner -> succeeded(shouldDeclareLostBeRefused(declareLostContext)),
        owner -> singleValidationError(NO_FEE_FINE_OWNER_FOUND,
          "locationId", declareLostContext.getLoan().getItem().getPermanentLocationId())))
      .thenApply(r -> r.map(notUsed -> declareLostContext));
  }

  private boolean shouldDeclareLostBeRefused(DeclareLostContext declaredLostContext) {
    log.debug("shouldDeclareLostBeRefused:: parameters loan: {}", declaredLostContext::getLoan);
    var lostItemPolicy = declaredLostContext.getLoan().getLostItemPolicy();

    boolean shouldDeclareLostBeRefused = declaredLostContext.getFeeFineOwner() == null
      && (lostItemPolicy.hasLostItemFee() || lostItemPolicy.hasLostItemProcessingFee());
    log.info("shouldDeclareLostBeRefused:: result: {}", shouldDeclareLostBeRefused);

    return shouldDeclareLostBeRefused;
  }
}
