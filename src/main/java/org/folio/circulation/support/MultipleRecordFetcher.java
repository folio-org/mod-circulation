package org.folio.circulation.support;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.collectingAndThen;
import static org.apache.commons.collections4.ListUtils.partition;
import static org.folio.circulation.domain.MultipleRecords.empty;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.CqlQuery.noQuery;
import static org.folio.circulation.support.http.client.PageLimit.maximumLimit;
import static org.folio.circulation.support.http.client.PageLimit.noLimit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class MultipleRecordFetcher<T> {
  private static final int DEFAULT_MAX_ID_VALUES_PER_CQL_SEARCH_QUERY = 50;

  private final GetManyRecordsClient client;
  private final String recordsPropertyName;
  private final Function<JsonObject, T> recordMapper;

  //Too many UUID values exceeds the allowed length of the HTTP request URL
  private final int maxValuesPerCqlSearchQuery;

  public MultipleRecordFetcher(GetManyRecordsClient client,
      String recordsPropertyName, Function<JsonObject, T> recordMapper) {

    this(client, recordsPropertyName, recordMapper,
      DEFAULT_MAX_ID_VALUES_PER_CQL_SEARCH_QUERY);
  }

  public MultipleRecordFetcher(GetManyRecordsClient client,
    String recordsPropertyName, Function<JsonObject, T> recordMapper,
    int maxValuesPerCqlSearchQuery) {

    this.client = client;
    this.recordsPropertyName = recordsPropertyName;
    this.recordMapper = recordMapper;
    this.maxValuesPerCqlSearchQuery = maxValuesPerCqlSearchQuery;
  }

  public CompletableFuture<Result<MultipleRecords<T>>> findByIds(
      Collection<String> ids) {

    return findByIndexName(ids, "id");
  }

  public CompletableFuture<Result<MultipleRecords<T>>> findByIndexName(
      Collection<String> ids, String indexName) {

    return findByIdIndexAndQuery(ids, indexName, noQuery());
  }

  public CompletableFuture<Result<MultipleRecords<T>>> findByIdIndexAndQuery(
      Collection<String> ids, String indexName, Result<CqlQuery> andQuery) {

    if (ids.isEmpty()) {
      return completedFuture(of(MultipleRecords::empty));
    }

    return findByBatchQueriesAndQuery(
      buildBatchQueriesByIndexName(ids, indexName), andQuery);
  }

  private CompletableFuture<Result<MultipleRecords<T>>> findByBatchQueriesAndQuery(
      List<Result<CqlQuery>> queries, Result<CqlQuery> andQuery) {

    return findByBatchQueries(queries.stream().map(query ->
      query.combine(andQuery, CqlQuery::and))
      .collect(Collectors.toList()));
  }

  private List<Result<CqlQuery>> buildBatchQueriesByIndexName(
      Collection<String> ids, String indexName) {

    return partition(new ArrayList<>(ids), maxValuesPerCqlSearchQuery)
      .stream()
      .map(partitionedIds -> exactMatchAny(indexName, partitionedIds))
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<MultipleRecords<T>>> findByBatchQueries(
      List<Result<CqlQuery>> queries) {

    // NOTE: query limit is max value to ensure all records are returned
    List<CompletableFuture<Result<MultipleRecords<T>>>> results = queries.stream()
        .map(query -> findByQuery(query, maximumLimit()))
        .collect(Collectors.toList());

    return CompletableFuture.allOf(results.toArray(new CompletableFuture[0]))
      .thenApply(notUsed -> results.stream()
        .map(CompletableFuture::join)
        .collect(collectingAndThen(Collectors.toList(), this::aggregate)));
  }

  public CompletableFuture<Result<MultipleRecords<T>>> findByQuery(
    Result<CqlQuery> queryResult) {

    return findByQuery(queryResult, noLimit());
  }

  private CompletableFuture<Result<MultipleRecords<T>>> findByQuery(
    Result<CqlQuery> queryResult, PageLimit pageLimit) {

    return queryResult.after(query -> client.getMany(query, pageLimit))
      .thenApply(result -> result.next(this::mapToRecords));
  }

  private Result<MultipleRecords<T>> mapToRecords(Response response) {
    return MultipleRecords.from(response, recordMapper, recordsPropertyName);
  }

  private Result<MultipleRecords<T>> aggregate(List<Result<MultipleRecords<T>>> results) {
    return Result.combineAll(results)
      .map(records -> records.stream().reduce(empty(), MultipleRecords::combine));
  }
}
