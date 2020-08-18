package org.folio.circulation.infrastructure.storage.notes;

import static org.folio.circulation.support.ResultBinding.flatMapResult;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notes.Note;
import org.folio.circulation.domain.notes.NoteType;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;

public class NotesRepository {
  private final CollectionResourceClient notesClient;
  private final CollectionResourceClient noteTypesClient;

  public static NotesRepository createUsing(Clients clients) {
    return new NotesRepository(clients.notesClient(), clients.noteTypesClient());
  }

  public NotesRepository(CollectionResourceClient notesClient,
    CollectionResourceClient noteTypesClient) {

    this.notesClient = notesClient;
    this.noteTypesClient = noteTypesClient;
  }

  public CompletableFuture<Result<Note>> create(Note note) {
    final NoteToJsonMapper noteToJsonMapper = new NoteToJsonMapper();

    final ResponseInterpreter<Note> interpreter = new ResponseInterpreter<Note>()
      .flatMapOn(201, mapUsingJson(noteToJsonMapper::noteFrom))
      .otherwise(forwardOnFailure());

    return notesClient.post(noteToJsonMapper.toJson(note))
      .thenApply(interpreter::flatMap);
  }

  public CompletableFuture<Result<MultipleRecords<NoteType>>> findGeneralNoteType() {
    return exactMatch("typeName", "General note")
      .after(query -> noteTypesClient.getMany(query, PageLimit.one()))
      .thenApply(flatMapResult(NotesRepository::mapResponseToNoteTypes));
  }

  private static Result<MultipleRecords<NoteType>> mapResponseToNoteTypes(Response response) {
    return MultipleRecords.from(response, NoteType::from, "noteTypes");
  }
}
