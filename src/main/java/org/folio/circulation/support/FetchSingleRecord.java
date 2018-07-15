package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

import java.util.function.Function;

import static org.folio.circulation.support.SingleRecordMapper.*;

public class FetchSingleRecord<T> {
  private final String recordType;
  private final CollectionResourceClient client;
  private final Function<JsonObject, T> mapper;

  private FetchSingleRecord(
    String recordType,
    CollectionResourceClient client,
    Function<JsonObject, T> mapper) {

    this.recordType = recordType;
    this.client = client;
    this.mapper = mapper;
  }

  public static <T> FetchSingleRecord<T> forRecord(String recordType) {
    return new FetchSingleRecord<>(recordType, null, null);
  }

  public FetchSingleRecord<T> using(CollectionResourceClient client) {
    return new FetchSingleRecord<>(recordType, client, mapper);
  }

  public FetchSingleRecord<T> mapTo(Function<JsonObject, T> mapper) {
    return new FetchSingleRecord<>(recordType, client, mapper);
  }

  public SingleRecordFetcher<T> whenNotFound(HttpResult<T> result) {
    return new SingleRecordFetcher<>(client, recordType, notFound(mapper, result));
  }
}
