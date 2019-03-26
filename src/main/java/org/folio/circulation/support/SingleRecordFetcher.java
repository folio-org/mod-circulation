package org.folio.circulation.support;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class SingleRecordFetcher<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient client;
  private final String recordType;
  private final SingleRecordMapper<T> mapper;

  public SingleRecordFetcher(
    CollectionResourceClient client,
    String recordType,
    SingleRecordMapper<T> mapper) {

    this.client = client;
    this.recordType = recordType;
    this.mapper = mapper;
  }

  public SingleRecordFetcher(
    CollectionResourceClient client,
    String recordType,
    Function<JsonObject, T> mapper) {

    this(client, recordType, new SingleRecordMapper<>(mapper));
  }

  public static SingleRecordFetcher<JsonObject> json(
    CollectionResourceClient client,
    String recordType,
    Function<Response, Result<JsonObject>> resultOnFailure) {

    return new SingleRecordFetcher<>(client, recordType,
      new SingleRecordMapper<>(identity(), resultOnFailure));
  }

  static SingleRecordFetcher<JsonObject> jsonOrNull(
    CollectionResourceClient client,
    String recordType) {

    return json(client, recordType, r -> succeeded(null));
  }

  public CompletableFuture<Result<T>> fetch(String id) {
    log.info("Fetching {} with ID: {}", recordType, id);

    return client.get(id)
      .thenApply(mapper::mapFrom)
      .exceptionally(e -> failed(new ServerErrorFailure(e)));
  }
}
