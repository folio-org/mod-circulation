package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.JsonStringArrayHelper.toList;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AnonymizeStorageLoansRepository {

  private final CollectionResourceClient loanStorageClient;

  public AnonymizeStorageLoansRepository(Clients clients) {
    loanStorageClient = clients.anonymizeStorageLoansClient();
  }

  private static ResponseInterpreter<LoanAnonymizationRecords>
  createStorageLoanResponseInterpreter(LoanAnonymizationRecords records) {

    Function<Response, Result<LoanAnonymizationRecords>> mapper = mapUsingJson(
        response -> records.withAnonymizedLoans(
            toList(response.getJsonArray("anonymizedLoans")))
    );
    return new ResponseInterpreter<LoanAnonymizationRecords>().flatMapOn(200, mapper)
      .otherwise(forwardOnFailure());
  }

  private static JsonObject createRequestPayload(LoanAnonymizationRecords records) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("loanIds", new JsonArray(records.getAnonymizedLoans()));
    return jsonObject;
  }

  public CompletableFuture<Result<LoanAnonymizationRecords>>
    postAnonymizeStorageLoans(LoanAnonymizationRecords records) {

    if (records.getAnonymizedLoans().isEmpty()) {
      return completedFuture(succeeded(records));
    }
    return loanStorageClient.post(createRequestPayload(records))
      .thenApply(createStorageLoanResponseInterpreter(records)::apply);
  }

}