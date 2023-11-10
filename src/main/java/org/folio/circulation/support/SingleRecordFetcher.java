package org.folio.circulation.support;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.logging.LogMessageSanitizer.sanitizeLogParameter;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.utils.LogUtil.mapAsString;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class SingleRecordFetcher<T> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient client;
  private final String recordType;
  private final ResponseInterpreter<T> interpreter;

  public SingleRecordFetcher(
    CollectionResourceClient client,
    String recordType,
    ResponseInterpreter<T> interpreter) {

    this.client = client;
    this.recordType = recordType;
    this.interpreter = interpreter;
  }

  public static SingleRecordFetcher<JsonObject> json(
    CollectionResourceClient client,
    String recordType,
    Function<Response, Result<JsonObject>> resultOnFailure) {

    return new SingleRecordFetcher<>(client, recordType,
      new ResponseInterpreter<JsonObject>()
        .flatMapOn(200, mapUsingJson(identity()))
        .otherwise(resultOnFailure));
  }

  public static SingleRecordFetcher<JsonObject> jsonOrNull(
    CollectionResourceClient client,
    String recordType) {

    return new SingleRecordFetcher<>(client, recordType,
      new ResponseInterpreter<JsonObject>()
        .flatMapOn(200, mapUsingJson(identity()))
        .otherwise(response -> succeeded(null)));
  }

  public CompletableFuture<Result<T>> fetch(String id) {
    log.info("Fetching {} with ID: {}", () -> recordType, () -> sanitizeLogParameter(id));

    requireNonNull(id, format("Cannot fetch single %s with null ID", recordType));

    return client.get(id)
      .thenApply(flatMapResult(interpreter::apply))
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  public CompletableFuture<Result<T>> fetchWithQueryStringParameters(Map<String, String> queryParameters) {
    log.info("Fetching {} with query parameters: {}", () -> recordType, () -> mapAsString(queryParameters));

    requireNonNull(queryParameters, format("Cannot fetch  %s with null parameters", recordType));

    return client.getManyWithQueryStringParameters(queryParameters)
      .thenApply(flatMapResult(interpreter::apply))
      .exceptionally(CommonFailures::failedDueToServerError);
  }
}
