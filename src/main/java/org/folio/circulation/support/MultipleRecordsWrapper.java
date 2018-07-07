package org.folio.circulation.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Collection;

public class MultipleRecordsWrapper {
  private static final String TOTAL_RECORDS_PROPERTY_NAME = "totalRecords";

  public static MultipleRecordsWrapper fromBody(
    String body,
    String recordsPropertyName) {

    return new MultipleRecordsWrapper(new JsonObject(body), recordsPropertyName);
  }

  private final Collection<JsonObject> records;
  private final Integer totalRecords;
  private final String recordsPropertyName;

  private MultipleRecordsWrapper(JsonObject wrapper, String recordsPropertyName) {
    this.recordsPropertyName = recordsPropertyName;
    this.records = JsonArrayHelper.toList(wrapper.getJsonArray(recordsPropertyName));
    this.totalRecords = wrapper.getInteger(TOTAL_RECORDS_PROPERTY_NAME);
  }

  public MultipleRecordsWrapper(
    Collection<JsonObject> records,
    String recordsPropertyName,
    Integer totalRecords) {

    this.recordsPropertyName = recordsPropertyName;
    this.records = records;
    this.totalRecords = totalRecords;
  }

  public Integer getTotalRecords() {
    return totalRecords;
  }

  public Collection<JsonObject> getRecords() {
    return records;
  }

  public boolean isEmpty() {
    return records.isEmpty();
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put(recordsPropertyName, new JsonArray(new ArrayList<>(records)))
      .put(TOTAL_RECORDS_PROPERTY_NAME, totalRecords);
  }
}
