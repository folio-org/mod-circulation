package org.folio.circulation.domain;

import static org.folio.circulation.support.ResultBinding.flatMapResult;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoteTypesRepository {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
