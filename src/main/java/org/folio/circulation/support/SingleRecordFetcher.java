package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.function.Function.identity;

public class SingleRecordFetcher<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient client;
  private final String recordType;
  private final SingleRecordMapper<T> mapper;

  public SingleRecordFetcher(
    CollectionResourceClient client,
    String recordType, SingleRecordMapper<T> mapper) {
    this.client = client;
    this.recordType = recordType;
    this.mapper = mapper;
  }

  SingleRecordFetcher(
    CollectionResourceClient client,
    String recordType,
    Function<JsonObject, T> mapper,
    Function<Response, HttpResult<T>> resultOnFailure) {

    this(client, recordType,
      new SingleRecordMapper<>(mapper, resultOnFailure));
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
    Function<Response, HttpResult<JsonObject>> resultOnFailure) {

    return new SingleRecordFetcher<>(client, recordType,
      new SingleRecordMapper<>(identity(), resultOnFailure));
  }

  public CompletableFuture<HttpResult<T>> fetchSingleRecord(String id) {
    log.info("Fetching {} with ID: {}", recordType, id);

    return client.get(id)
      .thenApply(mapper::mapFrom)
      .exceptionally(e -> HttpResult.failed(new ServerErrorFailure(e)));
  }
}
