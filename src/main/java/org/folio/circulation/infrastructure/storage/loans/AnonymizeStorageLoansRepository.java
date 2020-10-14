package org.folio.circulation.infrastructure.storage.loans;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

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
            toStream(response,"anonymizedLoans").collect(toList())));

    return new ResponseInterpreter<LoanAnonymizationRecords>().flatMapOn(200, mapper)
      .otherwise(forwardOnFailure());
  }

  private static JsonObject createRequestPayload(LoanAnonymizationRecords records) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("loanIds", new JsonArray(records.getAnonymizedLoanIds()));
    return jsonObject;
  }

  public CompletableFuture<Result<LoanAnonymizationRecords>>
    postAnonymizeStorageLoans(LoanAnonymizationRecords records) {

    if (records.getAnonymizedLoanIds().isEmpty()) {
      return completedFuture(succeeded(records));
    }
    return loanStorageClient.post(createRequestPayload(records))
      .thenApply(createStorageLoanResponseInterpreter(records)::flatMap);
  }
}
