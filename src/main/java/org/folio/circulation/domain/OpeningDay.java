package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;

import static org.folio.circulation.support.CalendarQueryUtil.getField;

public class OpeningDay {

  private static final String DATE_KEY = "date";
  private static final String ALL_DAY_KEY = "allDay";
  private static final String OPEN_KEY = "open";
  private static final String EXCEPTIONAL_KEY = "exceptional";
  private static final String OPENING_HOUR_KEY = "openingHour";

  private List<OpeningHour> openingHour;
  private String date;
  private boolean allDay;
  private boolean open;
  private boolean exceptional;

  OpeningDay(JsonObject jsonObject, String key) {
    JsonObject openingDayJson = jsonObject.getJsonObject(key);
    this.date = getField(openingDayJson.getString(DATE_KEY));
    this.allDay = openingDayJson.getBoolean(ALL_DAY_KEY, false);
    this.open = openingDayJson.getBoolean(OPEN_KEY, false);
    this.exceptional = openingDayJson.getBoolean(EXCEPTIONAL_KEY, false);
    this.openingHour = fillOpeningDay(openingDayJson);
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

  private OpeningDay(List<OpeningHour> openingHour, String date, boolean allDay, boolean open, boolean exceptional) {
    this.openingHour = openingHour;
    this.date = date;
    this.allDay = allDay;
    this.open = open;
    this.exceptional = exceptional;
  }

  public static OpeningDay createOpeningDay(List<OpeningHour> openingHour, String date, boolean allDay, boolean open, boolean exceptional) {
    return new OpeningDay(openingHour, date, allDay, open, exceptional);
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

  public boolean getExceptional() {
    return exceptional;
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
      .put(EXCEPTIONAL_KEY, exceptional)
      .put(OPENING_HOUR_KEY, openingHourToJsonArray());
  }
}
