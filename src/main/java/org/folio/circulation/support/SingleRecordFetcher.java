package org.folio.circulation.support;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.ResponseMapping.usingJson;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class SingleRecordFetcher<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
        .flatMapOn(200, usingJson(identity()))
        .otherwise(resultOnFailure));
  }

  static SingleRecordFetcher<JsonObject> jsonOrNull(
    CollectionResourceClient client,
    String recordType) {

    return new SingleRecordFetcher<>(client, recordType,
      new ResponseInterpreter<JsonObject>()
        .flatMapOn(200, usingJson(identity()))
        .otherwise(response -> succeeded(null)));
  }

  public CompletableFuture<Result<T>> fetch(String id) {
    log.info("Fetching {} with ID: {}", recordType, id);

    requireNonNull(id, format("Cannot fetch single %s with null ID", recordType));

    return client.get(id)
      .thenApply(interpreter::apply)
      .exceptionally(e -> failed(new ServerErrorFailure(e)));
  }
}
