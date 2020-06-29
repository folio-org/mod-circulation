package org.folio.circulation.resources;

<<<<<<< HEAD
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

=======
import java.time.LocalDateTime;
>>>>>>> d0e6bafd... Generating note for items declared lost
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.domain.NoteLinkType;
import org.folio.circulation.domain.NoteRepresentation;
import org.folio.circulation.domain.NoteType;
import org.folio.circulation.domain.NoteTypesRepository;
import org.folio.circulation.domain.NotesRepository;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.LostItemFeeChargingService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class DeclareLostResource extends Resource {

  private static final String NOTE_MESSAGE = "Claimed returned item marked lost";

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

    final NotesRepository notesRepo = new NotesRepository(clients);
    final NoteTypesRepository noteTypesRepo = new NoteTypesRepository(clients);

    validateDeclaredLostRequest(routingContext).after(request -> loanRepository.getById(request.getLoanId())
        .thenApply(LoanValidator::refuseWhenLoanIsClosed).thenApply(loan -> {
          boolean hasBeenClaimedReturned = loan.value().hasItemWithStatus(ItemStatus.CLAIMED_RETURNED);

          Result<Loan> loanResult = declareItemLost(loan, request);

          if (hasBeenClaimedReturned) {
            Optional<NoteType> noteType;
            try {
              noteType = noteTypesRepo.findBy("query=name==\"General note\"").get().value().getRecords().stream()
                  .findFirst();
              if(noteType.isPresent()) {
                notesRepo.create(new NoteRepresentation(NoteRepresentation.builder()
                  .withTitle(NOTE_MESSAGE)
                  .withTypeId(noteType.get().getId())
                  .withContent(NOTE_MESSAGE)
                  .withDate(LocalDateTime.now().toString())
                  .withLinks(
                    NoteLink.from(loan.value().getUserId(), NoteLinkType.USER.getValue())
                  )
                ));
              }
            } catch (InterruptedException | ExecutionException e) {
              e.printStackTrace();
            }
          }

          return loanResult;
        })
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
}
