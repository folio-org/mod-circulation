package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;

public class OpeningDay {

  private static final String DATE_KEY = "date";
  private static final String ALL_DAY_KEY = "allDay";
  private static final String OPEN_KEY = "open";
  private static final String OPENING_HOUR_KEY = "openingHour";

  private List<OpeningHour> openingHour;
  private String date;
  private boolean allDay;
  private boolean open;

  OpeningDay(JsonObject jsonObject, String key) {
    JsonObject openingDayJson = jsonObject.getJsonObject(key);
    this.date = StringUtils.defaultIfBlank(openingDayJson.getString(DATE_KEY), StringUtils.EMPTY);
    this.allDay = openingDayJson.getBoolean(ALL_DAY_KEY, false);
    this.open = openingDayJson.getBoolean(OPEN_KEY, false);
    this.openingHour = fillOpeningDay(openingDayJson);
  }

  private OpeningDay(List<OpeningHour> openingHour, String date, boolean allDay, boolean open) {
    this.openingHour = openingHour;
    this.date = date;
    this.allDay = allDay;
    this.open = open;
  }

  public static OpeningDay createOpeningDay(List<OpeningHour> openingHour, String date, boolean allDay, boolean open) {
    return new OpeningDay(openingHour, date, allDay, open);
  }

  private List<OpeningHour> fillOpeningDay(JsonObject representation) {
    List<OpeningHour> dayPeriods = new ArrayList<>();
    JsonArray openingHourJson = representation.getJsonArray(OPENING_HOUR_KEY);
    if (Objects.isNull(openingHourJson)) {
      return dayPeriods;
    }
    for (int i = 0; i < openingHourJson.size(); i++) {
      JsonObject jsonObject = openingHourJson.getJsonObject(i);
      dayPeriods.add(new OpeningHour(jsonObject));
    }
    return dayPeriods;
  }

  public String getDate() {
    return date;
  }

  public boolean getAllDay() {
    return allDay;
  }

  public boolean getOpen() {
    return open;
  }

  public List<OpeningHour> getOpeningHour() {
    return openingHour;
  }

  private JsonArray openingHourToJsonArray() {
    return openingHour.stream()
      .map(OpeningHour::toJson)
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add));
  }

  JsonObject toJson() {
    return new JsonObject()
      .put(DATE_KEY, date)
      .put(ALL_DAY_KEY, allDay)
      .put(OPEN_KEY, open)
      .put(OPENING_HOUR_KEY, openingHourToJsonArray());
  }
}
