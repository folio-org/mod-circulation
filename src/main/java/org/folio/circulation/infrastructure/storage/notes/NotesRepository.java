package org.folio.circulation.infrastructure.storage.notes;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Note;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;

public class NotesRepository {
  private final CollectionResourceClient notesClient;

  public NotesRepository(Clients clients) {
    notesClient = clients.notesClient();
  }

  public CompletableFuture<Result<Note>> create(Note note) {
    final ResponseInterpreter<Note> interpreter =
      new ResponseInterpreter<Note>()
        .flatMapOn(201, mapUsingJson(Note::from))
        .otherwise(forwardOnFailure());

    return notesClient.post(note.toJson())
      .thenApply(interpreter::flatMap);
  }
}
