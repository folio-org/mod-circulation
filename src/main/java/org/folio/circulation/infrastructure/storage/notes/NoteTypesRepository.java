package org.folio.circulation.infrastructure.storage.notes;

import static org.folio.circulation.support.ResultBinding.flatMapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.NoteType;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;

public class NoteTypesRepository {
  private final CollectionResourceClient noteTypesClient;

  public NoteTypesRepository(Clients clients) {
    noteTypesClient = clients.noteTypesClient();
  }

  public CompletableFuture<Result<MultipleRecords<NoteType>>> findByName(String name) {
    return CqlQuery.exactMatch("typeName", name)
      .after(query -> noteTypesClient.getMany(query, PageLimit.one()))
      .thenApply(flatMapResult(this::mapResponseToNoteTypes));
  }

  private Result<MultipleRecords<NoteType>> mapResponseToNoteTypes(Response response) {
    return MultipleRecords.from(response, NoteType::from, "noteTypes");
  }
}
