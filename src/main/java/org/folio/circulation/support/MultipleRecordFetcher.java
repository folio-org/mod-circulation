package org.folio.circulation.support;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class MultipleRecordFetcher<T> {
  private static final int MAX_BATCH_SIZE = 10;
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
      Collection<String> ids) {
    return findByIndexName(ids, "id");
  }

  public CompletableFuture<Result<MultipleRecords<T>>> findByIndexName(
      Collection<String> ids, String indexName) {
    if (ids.isEmpty()) {
      return completedFuture(of(MultipleRecords::empty));
    }
    return findByBatchQueries(buildBatchQueriesByIndexName(ids, indexName));
  }

  public CompletableFuture<Result<MultipleRecords<T>>> findByIndexNameAndQuery(
      Collection<String> ids, String indexName, Result<CqlQuery> andQuery) {
    if (ids.isEmpty()) {
      return completedFuture(of(MultipleRecords::empty));
    }
    return findByBatchQueriesAndQuery(buildBatchQueriesByIndexName(ids, indexName), andQuery);
  }

  private CompletableFuture<Result<MultipleRecords<T>>> findByBatchQueriesAndQuery(
      List<Result<CqlQuery>> queries, Result<CqlQuery> andQuery) {
    return findByBatchQueries(queries.stream().map(query ->
      andQuery.combine(query, CqlQuery::and))
      .collect(Collectors.toList()));
  }

  private List<Result<CqlQuery>> buildBatchQueriesByIndexName(
      Collection<String> ids, String indexName) {
    List<Result<CqlQuery>> queries = new ArrayList<>();
    List<String> idsList = new ArrayList<>(ids);
    while (!idsList.isEmpty()) {
      int currentSize = idsList.size() <= MAX_BATCH_SIZE ? idsList.size() : MAX_BATCH_SIZE;
      List<String> currentIds = idsList.subList(0, currentSize);
      queries.add(exactMatchAny(indexName, currentIds));
      idsList = idsList.subList(currentSize, idsList.size());
    }
    return queries;
  }

  private CompletableFuture<Result<MultipleRecords<T>>> findByBatchQueries(
      List<Result<CqlQuery>> queries) {
    // NOTE: query limit is max value to ensure all records are returned
    List<CompletableFuture<MultipleRecords<T>>> results = queries.stream()
        .map(query -> findByQuery(query, Integer.MAX_VALUE)
        .thenApply(result -> result.value()))
        .collect(Collectors.toList());
    return CompletableFuture.allOf(results.stream().toArray(CompletableFuture[]::new))
      .thenApply(notUsed -> results.stream()
        .map(records -> (MultipleRecords<T>) records.join())
        .collect(Collectors.collectingAndThen(Collectors.toList(), this::aggregate)));
  }

  private Result<MultipleRecords<T>> mapToRecords(Response response) {
    return MultipleRecords.from(response, recordMapper, recordsPropertyName);
  }

  public CompletableFuture<Result<MultipleRecords<T>>> findByQuery(
    Result<CqlQuery> queryResult) {
    return findByQuery(queryResult, null);
  }

  private CompletableFuture<Result<MultipleRecords<T>>> findByQuery(
    Result<CqlQuery> queryResult, Integer limit) {
    return queryResult.after(query -> client.getMany(query, limit))
      .thenApply(result -> result.next(this::mapToRecords));
  }

  private Result<MultipleRecords<T>> aggregate(List<MultipleRecords<T>> results) {
    final AtomicInteger totalRecords = new AtomicInteger(0);
    final List<T> wrappedRecords = results.stream()
      .flatMap(r -> {
        totalRecords.addAndGet(r.getTotalRecords());
        return r.getRecords().stream();
      }).collect(Collectors.toList());
    return succeeded(new MultipleRecords<>(wrappedRecords, totalRecords.get()));
  }
}
