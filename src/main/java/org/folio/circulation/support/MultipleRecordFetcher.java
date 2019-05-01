package org.folio.circulation.support;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class MultipleRecordFetcher<T> {
  private final CollectionResourceClient client;
  private final String recordsPropertyName;
  private final Function<JsonObject, T> recordMapper;

  public MultipleRecordFetcher(
    CollectionResourceClient client,
    String recordsPropertyName,
    Function<JsonObject, T> recordMapper) {

    this.client = client;
    this.recordsPropertyName = recordsPropertyName;
    this.recordMapper = recordMapper;
  }

  public CompletableFuture<Result<MultipleRecords<T>>> findByIds(
    List<String> idList) {

    String locationsQuery = CqlHelper.multipleRecordsCqlQuery(idList);

    return client.getMany(locationsQuery, idList.size(), 0)
      .thenApply(this::mapToRecords);
  }

  private Result<MultipleRecords<T>> mapToRecords(Response response) {
    return MultipleRecords.from(response, recordMapper,
      recordsPropertyName);
  }
}
