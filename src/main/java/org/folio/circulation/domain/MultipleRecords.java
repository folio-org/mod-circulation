package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.MultipleRecordsWrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MultipleRecords<T> {
  private final Collection<T> records;
  private final Integer totalRecords;

  public static <T> MultipleRecords<T> empty() {
    return new MultipleRecords<>(new ArrayList<>(), 0);
  }

  MultipleRecords(Collection<T> records, Integer totalRecords) {
    this.records = records;
    this.totalRecords = totalRecords;
  }

  //TODO: Maybe skip the wrapper and go straight to JSON?
  public MultipleRecordsWrapper mapToRepresentations(
    Function<T, JsonObject> mapper,
    String recordsPropertyName) {

    final List<JsonObject> mappedRequests = getRecords().stream()
      .map(mapper)
      .collect(Collectors.toList());

    return new MultipleRecordsWrapper(mappedRequests,
      recordsPropertyName, getTotalRecords());
  }

  public Collection<T> getRecords() {
    return records;
  }

  public Integer getTotalRecords() {
    return totalRecords;
  }
}
