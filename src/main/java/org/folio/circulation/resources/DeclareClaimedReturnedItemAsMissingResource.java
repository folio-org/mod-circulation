package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.domain.NoteLinkType;
import org.folio.circulation.domain.NoteRepresentation;
import org.folio.circulation.domain.NoteType;
import org.folio.circulation.domain.NoteTypesRepository;
import org.folio.circulation.domain.NotesRepository;
import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.folio.circulation.domain.validation.NotInItemStatusValidator;
import org.folio.circulation.services.ChangeItemStatusService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.utils.CollectionUtil;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class DeclareClaimedReturnedItemAsMissingResource extends Resource {

  private static final String NOTE_MESSAGE = "Claimed returned item marked missing";
  private static final String NOTE_DOMAIN  = "loans";

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

    createRequest(routingContext).after(request -> processDeclareClaimedReturnedItemAsMissing(routingContext, request))
        .thenApply(r -> r.toFixedValue(NoContentResponse::noContent)).thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<Loan>> processDeclareClaimedReturnedItemAsMissing(RoutingContext routingContext,
      ChangeItemStatusRequest request) {
    final Clients clients = Clients.create(new WebContext(routingContext), client);
    final ChangeItemStatusService changeItemStatusService = new ChangeItemStatusService(clients);

    return changeItemStatusService.getOpenLoan(request)
        .thenApply(NotInItemStatusValidator::refuseWhenItemIsNotClaimedReturned)
        .thenApply(loanResult -> declareLoanMissing(loanResult, request))
        .thenCompose(changeItemStatusService::updateLoanAndItem)
        .thenCompose(loanResult -> loanResult.after(loan -> createNote(clients, loan)));
  }

  private Result<Loan> declareLoanMissing(Result<Loan> loanResult, ChangeItemStatusRequest request) {
    return loanResult.map(loan -> loan.markItemMissing(request.getComment()));
  }

  private CompletableFuture<Result<Loan>> createNote(Clients clients, Loan loan) {
    final NotesRepository notesRepo = new NotesRepository(clients);
    final NoteTypesRepository noteTypesRepo = new NoteTypesRepository(clients);

    return noteTypesRepo.findByName("General note")
      .thenApply(this::refuseIfNoteTypeNotFound)
      .thenApply(r -> r.map(CollectionUtil::firstOrNull))
      .thenCompose(r -> r.after(noteType -> notesRepo.create(createNote(noteType, loan))))
      .thenApply(r -> r.map(notUsed -> loan));
  }

  private NoteRepresentation createNote(NoteType noteType, Loan loan) {
    return new NoteRepresentation(NoteRepresentation.builder()
      .withTitle(NOTE_MESSAGE)
      .withTypeId(noteType.getId())
      .withContent(NOTE_MESSAGE)
      .withDomain(NOTE_DOMAIN)
      .withLinks(NoteLink.from(loan.getUserId(), NoteLinkType.USER.getValue())));
  }

  private Result<MultipleRecords<NoteType>> refuseIfNoteTypeNotFound(
    Result<MultipleRecords<NoteType>> noteTypeResult) {

    return noteTypeResult.failWhen(
      notes -> Result.succeeded(notes.isEmpty()),
      notes -> singleValidationError("No General note type found", "noteTypes", null));
  }

  private Result<ChangeItemStatusRequest> createRequest(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();

    final ChangeItemStatusRequest request = ChangeItemStatusRequest.from(loanId, body);
    if (request.getComment() == null) {
      return failed(singleValidationError("Comment is a required field",
        ChangeItemStatusRequest.COMMENT, null));
    }

    return succeeded(request);
  }
}
