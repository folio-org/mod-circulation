package org.folio.circulation.domain.notice.session;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.http.ResponseMapping.flatMapUsingJson;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;

import io.vertx.core.json.JsonObject;

public class PatronActionSessionRepository {

  private static final String ID = "id";
  private static final String PATRON_ID = "patronId";
  private static final String LOAN_ID = "loanId";
  private static final String ACTION_TYPE = "actionType";

  public static PatronActionSessionRepository using(Clients clients) {
    return new PatronActionSessionRepository(clients.patronActionSessionsStorageClient());
  }

  private final CollectionResourceClient patronActionSessionsStorageClient;

  private PatronActionSessionRepository(CollectionResourceClient patronActionSessionsStorageClient) {
    this.patronActionSessionsStorageClient = patronActionSessionsStorageClient;
  }

  public CompletableFuture<Result<PatronSessionRecord>> create(PatronSessionRecord patronSessionRecord) {
    JsonObject representation = mapToJson(patronSessionRecord);

    final ResponseInterpreter<PatronSessionRecord> responseInterpreter
      = new ResponseInterpreter<PatronSessionRecord>()
      .flatMapOn(201, flatMapUsingJson(this::mapFromJson));

    return patronActionSessionsStorageClient.post(representation)
      .thenApply(responseInterpreter::apply);
  }

  private JsonObject mapToJson(PatronSessionRecord patronSessionRecord) {
    JsonObject json = new JsonObject();
    write(json, ID, patronSessionRecord.getId());
    write(json, PATRON_ID, patronSessionRecord.getPatronId());
    write(json, LOAN_ID, patronSessionRecord.getLoanId());
    write(json, ACTION_TYPE, patronSessionRecord.getActionType().getRepresentation());

    return json;
  }

  private Result<PatronSessionRecord> mapFromJson(JsonObject json) {
    UUID id = getUUIDProperty(json, ID);
    UUID patronId = getUUIDProperty(json, PATRON_ID);
    UUID loanId = getUUIDProperty(json, LOAN_ID);
    String actionTypeValue = getProperty(json, ACTION_TYPE);

    return PatronActionType.from(actionTypeValue)
      .map(patronActionType -> new PatronSessionRecord(id, patronId, loanId, patronActionType))
      .map(Result::succeeded)
      .orElse(failedDueToServerError("Invalid patron action type value: " + actionTypeValue));
  }
}
