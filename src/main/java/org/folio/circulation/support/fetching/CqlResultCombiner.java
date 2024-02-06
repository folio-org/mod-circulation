package org.folio.circulation.support.fetching;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.collectingAndThen;
import static org.folio.circulation.domain.MultipleRecords.empty;
import static org.folio.circulation.support.fetching.FetchUtil.buildBatchQueriesByIndexName;
import static org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria.byId;
import static org.folio.circulation.support.http.client.PageLimit.maximumLimit;
import static org.folio.circulation.support.results.Result.of;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.CombineWithMultipleCqlIndexValues;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

public class CqlResultCombiner<T, R> implements CombineWithMultipleCqlIndexValues<T, R> {

  private static final int DEFAULT_MAX_ID_VALUES_PER_CQL_SEARCH_QUERY = 50;
  private final FindWithCqlQuery<T> cqlFinder;
  private final Function<Result<MultipleRecords<T>>, Result<MultipleRecords<R>>> combiner;
  private final int maxValuesPerCqlSearchQuery;

  public CqlResultCombiner(FindWithCqlQuery<T> cqlFinder,
    Function<Result<MultipleRecords<T>>, Result<MultipleRecords<R>>> combiner,
    int maxValuesPerCqlSearchQuery) {

    this.cqlFinder = cqlFinder;
    this.combiner = combiner;
    this.maxValuesPerCqlSearchQuery = maxValuesPerCqlSearchQuery;
  }

  public CqlResultCombiner(FindWithCqlQuery<T> cqlFinder,
    Function<Result<MultipleRecords<T>>, Result<MultipleRecords<R>>> combiner) {

    this(cqlFinder, combiner, DEFAULT_MAX_ID_VALUES_PER_CQL_SEARCH_QUERY);
  }

  @Override
  public CompletableFuture<Result<MultipleRecords<R>>> findByIdsAndCombine(
    Collection<String> ids) {

    MultipleCqlIndexValuesCriteria criteria = byId(ids);
    if (criteria.getValues().isEmpty()) {
      return completedFuture(of(MultipleRecords::empty));
    }

    return findByBatchQueriesAndQuery(buildBatchQueriesByIndexName(criteria,
      maxValuesPerCqlSearchQuery), criteria.getAndQuery());
  }

  private CompletableFuture<Result<MultipleRecords<R>>> findByBatchQueriesAndQuery(
    List<Result<CqlQuery>> queries, Result<CqlQuery> andQuery) {

    return findByBatchQueries(queries.stream()
      .map(query -> query.combine(andQuery, CqlQuery::and))
      .collect(Collectors.toList()));
  }

  public CompletableFuture<Result<MultipleRecords<R>>> findByBatchQueries(
    List<Result<CqlQuery>> queries) {

    // NOTE: query limit is max value to ensure all records are returned
    List<CompletableFuture<Result<MultipleRecords<R>>>> results = queries.stream()
      .map(query -> cqlFinder.findByQuery(query, maximumLimit()))
      .map(pageRecords -> pageRecords.thenApply(combiner))
      .collect(Collectors.toList());

    return CompletableFuture.allOf(results.toArray(new CompletableFuture[0]))
      .thenApply(notUsed -> results.stream()
        .map(CompletableFuture::join)
        .collect(collectingAndThen(Collectors.toList(), this::aggregate)));
  }

  private Result<MultipleRecords<R>> aggregate(List<Result<MultipleRecords<R>>> results) {
    return Result.combineAll(results)
      .map(records -> records.stream().reduce(empty(), MultipleRecords::combine));
  }
}
