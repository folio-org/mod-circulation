package org.folio.circulation.support.fetching;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.collectingAndThen;
import static org.apache.commons.collections4.ListUtils.partition;
import static org.folio.circulation.domain.MultipleRecords.empty;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byId;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byIndex;
import static org.folio.circulation.support.http.client.PageLimit.maximumLimit;
import static org.folio.circulation.support.results.Result.of;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

import lombok.val;

public class CqlIndexValuesFinder<T> implements FindWithMultipleCqlIndexValues<T> {
  private static final int DEFAULT_MAX_ID_VALUES_PER_CQL_SEARCH_QUERY = 50;

  private final FindWithCqlQuery<T> cqlFinder;
  private final int maxValuesPerCqlSearchQuery;

  public CqlIndexValuesFinder(FindWithCqlQuery<T> cqlFinder,
    int maxValuesPerCqlSearchQuery) {

    this.cqlFinder = cqlFinder;
    this.maxValuesPerCqlSearchQuery = maxValuesPerCqlSearchQuery;
  }

  public CqlIndexValuesFinder(FindWithCqlQuery<T> cqlFinder) {
    this(cqlFinder, DEFAULT_MAX_ID_VALUES_PER_CQL_SEARCH_QUERY);
  }

  @Override
  public CompletableFuture<Result<MultipleRecords<T>>> findByIds(
    Collection<String> ids) {

    return find(byId(ids));
  }

  @Override
  public CompletableFuture<Result<MultipleRecords<T>>> findByIdIndexAndQuery(
    Collection<String> values, String indexName, Result<CqlQuery> andQuery) {

    return find(byIndex(indexName, values)
      .withQuery(andQuery));
  }

  @Override
  public CompletableFuture<Result<MultipleRecords<T>>> find(
    MultipleCqlIndexValuesCriteria criteria) {

    if (criteria.getValues().isEmpty()) {
      return completedFuture(of(MultipleRecords::empty));
    }

    return findByBatchQueriesAndQuery(buildBatchQueriesByIndexName(criteria),
      criteria.getAndQuery());
  }

  private CompletableFuture<Result<MultipleRecords<T>>> findByBatchQueriesAndQuery(
    List<Result<CqlQuery>> queries, Result<CqlQuery> andQuery) {

    return findByBatchQueries(queries.stream().map(query ->
      query.combine(andQuery, CqlQuery::and))
      .collect(Collectors.toList()));
  }

  private List<Result<CqlQuery>> buildBatchQueriesByIndexName(MultipleCqlIndexValuesCriteria criteria) {
    val indexName = criteria.getIndexName();
    val indexOperator = criteria.getIndexOperator();
    val values = criteria.getValues();

    return partition(new ArrayList<>(values), maxValuesPerCqlSearchQuery)
      .stream()
      .map(partitionedIds -> indexOperator.apply(indexName, partitionedIds))
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<MultipleRecords<T>>> findByBatchQueries(
    List<Result<CqlQuery>> queries) {

    // NOTE: query limit is max value to ensure all records are returned
    List<CompletableFuture<Result<MultipleRecords<T>>>> results = queries.stream()
      .map(query -> cqlFinder.findByQuery(query, maximumLimit()))
      .collect(Collectors.toList());

    return CompletableFuture.allOf(results.toArray(new CompletableFuture[0]))
      .thenApply(notUsed -> results.stream()
        .map(CompletableFuture::join)
        .collect(collectingAndThen(Collectors.toList(), this::aggregate)));
  }

  private Result<MultipleRecords<T>> aggregate(
    List<Result<MultipleRecords<T>>> results) {

    return Result.combineAll(results)
      .map(records -> records.stream().reduce(empty(), MultipleRecords::combine));
  }
}
