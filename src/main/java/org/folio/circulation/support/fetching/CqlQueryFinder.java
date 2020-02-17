package org.folio.circulation.support.fetching;

import static org.folio.circulation.support.http.client.PageLimit.noLimit;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class CqlQueryFinder<T> implements FindWithCqlQuery<T> {
  private final GetManyRecordsClient client;
  private final String recordsPropertyName;
  private final Function<JsonObject, T> recordMapper;

  public CqlQueryFinder(GetManyRecordsClient client,
    String recordsPropertyName, Function<JsonObject, T> recordMapper) {

    this.client = client;
    this.recordsPropertyName = recordsPropertyName;
    this.recordMapper = recordMapper;
  }

  @Override
  public CompletableFuture<Result<MultipleRecords<T>>> findByQuery(
    Result<CqlQuery> queryResult) {

    return findByQuery(queryResult, noLimit());
  }

  @Override
  public CompletableFuture<Result<MultipleRecords<T>>> findByQuery(
    Result<CqlQuery> queryResult, PageLimit pageLimit) {

    return queryResult.after(query -> client.getMany(query, pageLimit))
      .thenApply(result -> result.next(this::mapToRecords));
  }

  private Result<MultipleRecords<T>> mapToRecords(Response response) {
    return MultipleRecords.from(response, recordMapper, recordsPropertyName);
  }
}
