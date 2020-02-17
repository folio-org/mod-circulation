package org.folio.circulation.support.fetching;

import java.util.function.Function;

import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.MultipleRecordFetcher;

import io.vertx.core.json.JsonObject;

public class RecordFetching {
  private RecordFetching() { }

  public static <T> FindWithMultipleCqlIndexValues<T> findWithMultipleCqlIndexValues(
      GetManyRecordsClient client, String recordsPropertyName,
      Function<JsonObject, T> recordMapper) {

    return new MultipleRecordFetcher<>(client, recordsPropertyName, recordMapper);
  }

  public static <T> MultipleRecordFetcher<T> findWithCqlQuery(
    GetManyRecordsClient client, String recordsPropertyName,
    Function<JsonObject, T> recordMapper) {

    return new MultipleRecordFetcher<>(client, recordsPropertyName, recordMapper);
  }
}
