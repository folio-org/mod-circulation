package org.folio.circulation.domain;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.JsonArrayHelper.mapToList;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MultipleRecords<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String TOTAL_RECORDS_PROPERTY_NAME = "totalRecords";

  private final Collection<T> records;
  private final Integer totalRecords;

  public MultipleRecords(Collection<T> records, Integer totalRecords) {
    this.records = records;
    this.totalRecords = totalRecords;
  }

  public static <T> HttpResult<MultipleRecords<T>> from(
    Response response,
    Function<JsonObject, T> mapper,
    String recordsPropertyName) {

    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() != 200) {
        return failed(new ServerErrorFailure(
          String.format("Failed to fetch %s from storage (%s:%s)",
            recordsPropertyName, response.getStatusCode(), response.getBody())));
      }

      final JsonObject json = response.getJson();

      List<T> wrappedRecords = mapToList(json,
        recordsPropertyName, mapper);

      Integer totalRecords = json.getInteger(TOTAL_RECORDS_PROPERTY_NAME);

      return succeeded(new MultipleRecords<>(
        wrappedRecords, totalRecords));
    }
    else {
      log.warn("Did not receive response to request");
      return failed(new ServerErrorFailure(
        String.format("Did not receive response to request for multiple %s",
          recordsPropertyName)));
    }
  }

  Map<String, T> toMap(Function<T, String> keyMapper) {
    return getRecords().stream().collect(
      Collectors.toMap(keyMapper, identity()));
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

  public JsonObject asJson(
    Function<T, JsonObject> mapper,
    String recordsPropertyName) {

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

  public Integer getTotalRecords() {
    return totalRecords;
  }
}
