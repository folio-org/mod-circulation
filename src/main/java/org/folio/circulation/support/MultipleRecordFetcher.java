package org.folio.circulation.support;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.Result.of;

import java.util.Collection;
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

  public CompletableFuture<Result<MultipleRecords<T>>> findByIds(Collection<String> idList) {
    if(idList.isEmpty()) {
      return completedFuture(of(MultipleRecords::empty));
    }

    return findByIndexName(idList, "id");
  }

  public CompletableFuture<Result<MultipleRecords<T>>> findByIndexName(Collection<String> idList, String indexName) {
    if(idList.isEmpty()) {
      return completedFuture(of(MultipleRecords::empty));
    }

    return exactMatchAny(indexName, idList)
      .after(query -> client.getMany(query, null))
      .thenApply(result -> result.next(this::mapToRecords));
  }

  private Result<MultipleRecords<T>> mapToRecords(Response response) {
    return MultipleRecords.from(response, recordMapper, recordsPropertyName);
  }
}
