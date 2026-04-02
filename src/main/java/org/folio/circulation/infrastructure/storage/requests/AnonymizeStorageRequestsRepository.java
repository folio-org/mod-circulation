package org.folio.circulation.infrastructure.storage.requests;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.HttpStatus.HTTP_CREATED;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.anonymization.RequestAnonymizationRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.storage.RequestBatch;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AnonymizeStorageRequestsRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient requestStorageClient;

  public AnonymizeStorageRequestsRepository(Clients clients) {
    requestStorageClient = clients.requestsBatchStorage();
  }

  private static ResponseInterpreter<RequestAnonymizationRecords>
  createStorageRequestResponseInterpreter(RequestAnonymizationRecords records) {

    Function<Response, Result<RequestAnonymizationRecords>> mapper = mapUsingJson(
      response -> {
        var anonymized = toStream(response, "anonymizedRequests").toList();
        log.info("createStorageRequestResponseInterpreter:: successfully anonymized {} requests",
          anonymized.size());
        return records.withAnonymizedRequests(anonymized);
      });

    return new ResponseInterpreter<RequestAnonymizationRecords>()
      .on(HTTP_CREATED.toInt(), Result.of(() -> records))
      .otherwise(forwardOnFailure());
  }

  public CompletableFuture<Result<RequestAnonymizationRecords>>
  postAnonymizeStorageRequests(RequestAnonymizationRecords records) {

    log.debug("postAnonymizeStorageRequests:: parameters records: {}", records);

    if (records.getAnonymizedRequestIds().isEmpty()) {
      log.info("postAnonymizeStorageRequests:: no requests to anonymize, skipping");
      return completedFuture(succeeded(records));
    }

    log.info("postAnonymizeStorageRequests:: anonymizing {} requests",
      records.getAnonymizedRequestIds().size());
    RequestBatch requestBatch = new RequestBatch(records.getAnonymizedRequests());

    return requestStorageClient.post(requestBatch.toJson())
      .thenApply(createStorageRequestResponseInterpreter(records)::flatMap);
  }
}
