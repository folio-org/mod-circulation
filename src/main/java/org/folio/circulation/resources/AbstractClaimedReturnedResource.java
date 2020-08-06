package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Note;
import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.domain.NoteLinkType;
import org.folio.circulation.domain.NoteType;
import org.folio.circulation.domain.validation.GeneralNoteTypeValidator;
import org.folio.circulation.infrastructure.storage.notes.NoteTypesRepository;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.utils.CollectionUtil;

import io.vertx.core.http.HttpClient;

public abstract class AbstractClaimedReturnedResource extends Resource {
  protected static final String NOTE_MESSAGE = "Claimed returned item marked lost";
  protected static final String NOTE_DOMAIN  = "loans";

  protected boolean isClaimedReturned;

  public AbstractClaimedReturnedResource(HttpClient client) {
    super(client);
  }

  protected  Result<Loan> setIsClaimedReturned(Result<Loan> loanResult) {
    loanResult.map(loan -> isClaimedReturned = loan.isClaimedReturned());
    return loanResult;
  }

  protected CompletableFuture<Result<Loan>> createNote(Clients clients, Loan loan, boolean isClaimedReturned) {
    final NotesRepository notesRepo = new NotesRepository(clients);
    final NoteTypesRepository noteTypesRepo = new NoteTypesRepository(clients);
    if (isClaimedReturned) {
      return noteTypesRepo.findByName("General note")
        .thenApply(GeneralNoteTypeValidator::refuseIfNoteTypeNotFound)
        .thenApply(r -> r.map(CollectionUtil::firstOrNull))
        .thenCompose(r -> r.after(noteType -> notesRepo.create(createNote(noteType, loan))))
        .thenCompose(r -> r.after(note -> completedFuture(succeeded(loan))));
    }
    return completedFuture(succeeded(loan));
  }

  protected Note createNote(NoteType noteType, Loan loan) {
    return Note.builder()
      .title(NOTE_MESSAGE)
      .typeId(noteType.getId())
      .content(NOTE_MESSAGE)
      .domain(NOTE_DOMAIN)
      .link(NoteLink.from(loan.getUserId(), NoteLinkType.USER.getValue()))
      .build();
  }
}
