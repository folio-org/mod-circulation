package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class LibraryHours {

  private static final String OPENING_PERIODS_KEY = "openingPeriods";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";

  private int totalRecords;
  private List<Calendar> openingPeriods;

  private JsonObject representation;

  LibraryHours(JsonObject representation) {
    this.representation = representation;
    this.totalRecords = representation.getInteger(TOTAL_RECORDS_KEY);
    initOpeningPeriods();
  }

  LibraryHours() {
  }

  private void initOpeningPeriods() {
    List<Calendar> calendarList = new ArrayList<>();
    JsonArray jsonArray = representation.getJsonArray(OPENING_PERIODS_KEY);
    for (int i = 0; i < jsonArray.size(); i++) {
      JsonObject jsonObject = jsonArray.getJsonObject(i);
      calendarList.add(new Calendar(jsonObject));
    }
    this.openingPeriods = calendarList;
  }

  public int getTotalRecords() {
    return totalRecords;
  }

  public List<Calendar> getOpeningPeriods() {
    return openingPeriods;
  }
}
