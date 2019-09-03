package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AnonymizeStorageLoansRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup()
    .lookupClass());
  private final CollectionResourceClient loanStorageClient;

  public AnonymizeStorageLoansRepository(Clients clients) {
    loanStorageClient = clients.anonymizeStorageLoansClient();
  }

  /**
   * Fill the records with the response from /anonymize-storage-loans
   *
   */
  private static ResponseInterpreter<LoanAnonymizationRecords> createStorageLoanResponseInterpreter(
      LoanAnonymizationRecords records) {
    Function<Response, Result<LoanAnonymizationRecords>> mapper = mapUsingJson(
        response -> records.withAnonymizedLoans(response.getJsonArray("anonymizedLoans")
          .getList()));
    return new ResponseInterpreter<LoanAnonymizationRecords>().flatMapOn(200, mapper)
      .otherwise(forwardOnFailure());
  }

  /**
   * Create a request payload to loan storage /anonymize-storage-loans
   *
   */
  private static JsonObject createRequestPayload(LoanAnonymizationRecords records) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("loanIds", new JsonArray(records.getAnonymizedLoans()));
    return jsonObject;
  }

  /**
   * Send POST request to /anonymize-storage-loans
   */
  public CompletableFuture<Result<LoanAnonymizationRecords>> postAnonymizeStorageLoans(LoanAnonymizationRecords records) {

    log.info("Calling POST /anonymize-storage-loans");
    if (records.getAnonymizedLoans()
      .isEmpty()) {
      return completedFuture(succeeded(records));
    }
    return loanStorageClient.post(createRequestPayload(records))
      .thenApply(createStorageLoanResponseInterpreter(records)::apply);
  }

}