package org.folio.circulation.infrastructure.storage.loans;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AnonymizeStorageLoansRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * Ids per strip request, so a large page becomes several bounded
   * {@code UPDATE ... WHERE id IN (...)} statements rather than one oversized
   * one. Chunks are posted sequentially.
   */
  private static final int STRIP_CHUNK_SIZE = 2500;

  private final CollectionResourceClient loanStorageClient;

  public AnonymizeStorageLoansRepository(Clients clients) {
    loanStorageClient = clients.anonymizeStorageLoansClient();
  }

  private static ResponseInterpreter<List<String>> chunkResponseInterpreter() {
    Function<Response, Result<List<String>>> mapper = mapUsingJson(
        response -> toStream(response, "anonymizedLoans").toList());

    return new ResponseInterpreter<List<String>>().flatMapOn(200, mapper)
      .otherwise(forwardOnFailure());
  }

  private static JsonObject createRequestPayload(List<String> loanIds) {
    return new JsonObject().put("loanIds", new JsonArray(loanIds));
  }

  public CompletableFuture<Result<LoanAnonymizationRecords>>
    postAnonymizeStorageLoans(LoanAnonymizationRecords records) {

    final List<String> allIds = records.getAnonymizedLoanIds();
    if (allIds.isEmpty()) {
      return completedFuture(succeeded(records));
    }

    if (allIds.size() > STRIP_CHUNK_SIZE) {
      log.info("postAnonymizeStorageLoans:: stripping {} loans in chunks of {}",
        allIds.size(), STRIP_CHUNK_SIZE);
    }

    CompletableFuture<Result<List<String>>> anonymized =
      completedFuture(succeeded(new ArrayList<>()));

    for (int from = 0; from < allIds.size(); from += STRIP_CHUNK_SIZE) {
      final List<String> chunk =
        allIds.subList(from, Math.min(from + STRIP_CHUNK_SIZE, allIds.size()));

      anonymized = anonymized.thenCompose(r -> r.after(collected ->
        loanStorageClient.post(createRequestPayload(chunk))
          .thenApply(chunkResponseInterpreter()::flatMap)
          .thenApply(chunkResult -> chunkResult.map(chunkIds -> {
            collected.addAll(chunkIds);
            return collected;
          }))));
    }

    return anonymized
      .thenApply(r -> r.map(records::withAnonymizedLoans));
  }
}
