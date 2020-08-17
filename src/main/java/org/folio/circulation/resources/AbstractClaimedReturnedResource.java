package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.notes.NoteCreator;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

import io.vertx.core.http.HttpClient;

public abstract class AbstractClaimedReturnedResource extends Resource {
  protected boolean isClaimedReturned;

  public AbstractClaimedReturnedResource(HttpClient client) {
    super(client);
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
