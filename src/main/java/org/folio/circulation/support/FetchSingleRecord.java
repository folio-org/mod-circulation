package org.folio.circulation.support;

import static org.folio.circulation.support.ResponseMapping.usingJson;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.support.http.client.ResponseInterpreter;

import io.vertx.core.json.JsonObject;

public class FetchSingleRecord<T> {
  private final String recordType;
  private final CollectionResourceClient client;
  private final ResponseInterpreter<T> interpreter;

  private FetchSingleRecord(
    String recordType,
    CollectionResourceClient client,
    ResponseInterpreter<T> interpreter) {

    this.recordType = recordType;
    this.client = client;
    this.interpreter = interpreter;
  }

  public static <T> FetchSingleRecord<T> forRecord(String recordType) {
    return new FetchSingleRecord<>(recordType, null, new ResponseInterpreter<T>());
  }

  public FetchSingleRecord<T> using(CollectionResourceClient client) {
    return new FetchSingleRecord<>(recordType, client, interpreter);
  }

  public FetchSingleRecord<T> mapTo(Function<JsonObject, T> mapper) {
    return new FetchSingleRecord<>(recordType, client,
      interpreter.flatMapOn(200, usingJson(mapper)));
  }

  public FetchSingleRecord<T> whenNotFound(Result<T> result) {
    return new FetchSingleRecord<>(recordType, client, interpreter
      .on(404, result));
  }

  public CompletableFuture<Result<T>> fetch(String id) {
    return new SingleRecordFetcher<>(client, recordType, interpreter)
      .fetch(id);
  }
}
