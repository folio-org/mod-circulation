package org.folio.circulation.infrastructure.storage.requests;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.anonymization.RequestAnonymizationRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AnonymizeStorageRequestsRepository {
  private final CollectionResourceClient requestStorageClient;

  public AnonymizeStorageRequestsRepository(Clients clients) {
    requestStorageClient = clients.anonymizeStorageRequestsClient();
  }

  private static ResponseInterpreter<RequestAnonymizationRecords>
  createStorageRequestResponseInterpreter(RequestAnonymizationRecords records) {

    Function<Response, Result<RequestAnonymizationRecords>> mapper = mapUsingJson(
      response -> records.withAnonymizedRequests(
        toStream(response, "anonymizedRequests").collect(toList())));

    return new ResponseInterpreter<RequestAnonymizationRecords>().flatMapOn(200, mapper)
      .otherwise(forwardOnFailure());
  }

  private static JsonObject createRequestPayload(RequestAnonymizationRecords records) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("requestIds", new JsonArray(records.getAnonymizedRequestIds()));
    return jsonObject;
  }

  public CompletableFuture<Result<RequestAnonymizationRecords>>
  postAnonymizeStorageRequests(RequestAnonymizationRecords records) {

    if (records.getAnonymizedRequestIds().isEmpty()) {
      return completedFuture(succeeded(records));
    }
    return requestStorageClient.post(createRequestPayload(records))
      .thenApply(createStorageRequestResponseInterpreter(records)::flatMap);
  }
}
