package org.folio.circulation.domain;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotesRepository {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  private final CollectionResourceClient notesClient;

  public NotesRepository(Clients clients) {
    notesClient = clients.notesClient();
  }

  public CompletableFuture<Result<Note>> create(NoteRepresentation note) {
    final ResponseInterpreter<Note> interpreter =
      new ResponseInterpreter<Note>()
        .flatMapOn(201, mapUsingJson(Note::from))
        .otherwise(forwardOnFailure());

    return notesClient.post(note)
      .thenApply(interpreter::flatMap);
  }

}