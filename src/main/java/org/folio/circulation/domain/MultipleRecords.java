package org.folio.circulation.domain;

import static java.util.function.Function.identity;
import static java.util.stream.Stream.concat;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.mapAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MultipleRecords<T> {
  private static final String TOTAL_RECORDS_PROPERTY_NAME = "totalRecords";
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Collection<T> records;
  private final Integer totalRecords;

  public MultipleRecords(Collection<T> records, Integer totalRecords) {
    this.records = records;
    this.totalRecords = totalRecords;
  }

  public static <T> MultipleRecords<T> empty() {
    return new MultipleRecords<>(new ArrayList<>(), 0);
  }

  public static <T> Result<MultipleRecords<T>> from(Response response,
    Function<JsonObject, T> mapper, String recordsPropertyName) {

    log.debug("from:: parameters response: {}, recordsPropertyName: {}",
      response, recordsPropertyName);

    return new ResponseInterpreter<MultipleRecords<T>>()
      .flatMapOn(200, r -> from(r.getJson(), mapper, recordsPropertyName))
      .apply(response);
  }

  public static <T> Result<MultipleRecords<T>> from(JsonObject representation,
    Function<JsonObject, T> mapper, String recordsPropertyName) {

    log.debug("from:: parameters representation: {}, recordsPropertyName: {}",
      () -> representation, () -> recordsPropertyName);

    List<T> wrappedRecords = mapToList(representation, recordsPropertyName, mapper);
    Integer totalRecords = representation.getInteger(TOTAL_RECORDS_PROPERTY_NAME);

    return succeeded(new MultipleRecords<>(
      wrappedRecords, totalRecords));
  }

  public <R> MultipleRecords<T> combineRecords(MultipleRecords<R> otherRecords,
    Function<T, Predicate<R>> matcher,
    BiFunction<T, R, T> combiner, R defaultOtherRecord) {

    log.debug("combineRecords:: parameters otherRecords: {}",
      () -> multipleRecordsAsString(otherRecords));

    return mapRecords(mainRecord -> combiner.apply(mainRecord, otherRecords
      .filter(matcher.apply(mainRecord))
      .firstOrElse(defaultOtherRecord)));
  }

  /**
   * Avoids looping through the elements of otherRecords
   */
  public <R> MultipleRecords<T> combineRecords(Map<String, R> otherRecordsMap,
    Function<T, String> keyMapper, BiFunction<T, R, T> combiner, R defaultOtherRecord) {

    log.debug("combineRecords:: parameters otherRecordsMap: {}",
      () -> mapAsString(otherRecordsMap));

    return mapRecords(mainRecord -> combiner.apply(mainRecord,
      otherRecordsMap.getOrDefault(keyMapper.apply(mainRecord), defaultOtherRecord)));
  }

  public T firstOrNull() {
    return firstOrElse(null);
  }

  public T firstOrElse(T other) {
    return getRecords().stream().findFirst().orElse(other);
  }

  public <R> Set<R> toKeys(Function<T, R> keyMapper) {
    return getRecords().stream()
      .filter(Objects::nonNull)
      .map(keyMapper)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  public Map<String, T> toMap(Function<T, String> keyMapper) {
    return getRecords().stream()
      .collect(Collectors.toMap(keyMapper, identity(),
        (record1, record2) -> record1));
  }

  /**
   * Maps the records within a multiple records collection
   * using the providing mapping function
   * @param mapper function to map each record to new record
   * @param <R> Type of record to map to
   * @return new multiple records collection with mapped records
   * and same total record count
   */
  public <R> MultipleRecords<R> mapRecords(Function<T, R> mapper) {
    return new MultipleRecords<>(
      getRecords().stream().map(mapper).collect(Collectors.toList()),
        getTotalRecords());
  }

  public <R> Result<MultipleRecords<R>> flatMapRecords(Function<T, Result<R>> mapper) {
    List<Result<R>> mappedRecordsList = records.stream()
      .map(mapper).collect(Collectors.toList());

    Result<List<R>> combinedResult = Result.combineAll(mappedRecordsList);
    return combinedResult.map(list -> new MultipleRecords<>(list, totalRecords));
  }

  public MultipleRecords<T> combine(MultipleRecords<T> other) {
    log.debug("combine:: parameters other: {}", () -> multipleRecordsAsString(other));
    final List<T> allRecords = concat(records.stream(), other.records.stream())
      .collect(Collectors.toList());

    return new MultipleRecords<>(allRecords, totalRecords + other.totalRecords);
  }

  public MultipleRecords<T> filter(Predicate<T> predicate) {
    final List<T> filteredRecords = getRecords().stream()
      .filter(predicate)
      .collect(Collectors.toList());

    final int numberOfFilteredOutRecords = totalRecords - filteredRecords.size();
    return new MultipleRecords<>(filteredRecords, totalRecords - numberOfFilteredOutRecords);
  }

  public JsonObject asJson(Function<T, JsonObject> mapper, String recordsPropertyName) {
    final List<JsonObject> mappedRecords = getRecords().stream()
      .map(mapper)
      .collect(Collectors.toList());

    return new JsonObject()
      .put(recordsPropertyName, new JsonArray(mappedRecords))
      .put(TOTAL_RECORDS_PROPERTY_NAME, totalRecords);
  }

  public Collection<T> getRecords() {
    return records;
  }

  public Map<String, T> getRecordsMap(Function<T, String> keyMapper) {
    return records.stream().collect(Collectors.toMap(keyMapper, identity()));
  }

  public Integer getTotalRecords() {
    return totalRecords;
  }

  public boolean isEmpty() {
    return records.isEmpty();
  }

  public int size() {
    return records.size();
  }

  public static class CombinationMatchers {
    private CombinationMatchers() { }

    public static <T, R> Function<T, Predicate<R>> matchRecordsById(
      Function<T, String> idFromMainRecord,
      Function<R, String> idFromOtherRecord) {

      return mainRecord -> otherRecord
        -> Objects.equals(idFromMainRecord.apply(mainRecord), idFromOtherRecord.apply(otherRecord));
    }
  }
}
