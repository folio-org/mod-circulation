package org.folio.circulation.support.fetching;

import java.util.function.Function;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.FindByIdsAndCombine;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class RecordFetching {
  private RecordFetching() { }

  public static <T> FindWithMultipleCqlIndexValues<T> findWithMultipleCqlIndexValues(
      GetManyRecordsClient client, String recordsPropertyName,
      Function<JsonObject, T> recordMapper) {

    return new CqlIndexValuesFinder<>(
      new CqlQueryFinder<>(client, recordsPropertyName, recordMapper));
  }

  public static <T> CqlQueryFinder<T> findWithCqlQuery(
    GetManyRecordsClient client, String recordsPropertyName,
    Function<JsonObject, T> recordMapper) {

    return new CqlQueryFinder<>(client, recordsPropertyName, recordMapper);
  }

  public static <T, R> FindByIdsAndCombine<R> findWithMultipleCqlIndexValuesAndCombine(
    GetManyRecordsClient client, String recordsPropertyName, Function<JsonObject, T> recordMapper,
    Function<Result<MultipleRecords<T>>, Result<MultipleRecords<R>>> combineFunction) {

    return new CqlResultCombiner<>(
      new CqlQueryFinder<>(client, recordsPropertyName, recordMapper), combineFunction);
  }
}
