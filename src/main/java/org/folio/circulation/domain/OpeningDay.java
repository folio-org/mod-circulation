package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;

public class OpeningDay {

  public static OpeningDay createClosedDay() {
    return createOpeningDay(Collections.emptyList(), null, true, false);
  }

  private static final String DATE_KEY = "date";
  private static final String ALL_DAY_KEY = "allDay";
  private static final String OPEN_KEY = "open";
  private static final String OPENING_HOUR_KEY = "openingHour";
  private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'Z'";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
    DateTimeFormat.forPattern(DATE_TIME_FORMAT);

  private List<OpeningHour> openingHour;
  private LocalDate date;
  private boolean allDay;
  private boolean open;

  OpeningDay(JsonObject jsonObject, String key) {
    JsonObject openingDayJson = jsonObject.getJsonObject(key);
    String dateProperty = openingDayJson.getString(DATE_KEY);
    if (dateProperty != null) {
      this.date = LocalDate.parse(dateProperty, DATE_TIME_FORMATTER);
    }
    this.allDay = openingDayJson.getBoolean(ALL_DAY_KEY, false);
    this.open = openingDayJson.getBoolean(OPEN_KEY, false);
    this.openingHour = fillOpeningDay(openingDayJson);
  }

  private OpeningDay(List<OpeningHour> openingHour, LocalDate date, boolean allDay, boolean open) {
    this.openingHour = openingHour;
    this.date = date;
    this.allDay = allDay;
    this.open = open;
  }

  public static OpeningDay createOpeningDay(List<OpeningHour> openingHour, LocalDate date, boolean allDay, boolean open) {
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

  public LocalDate getDate() {
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
      .put(DATE_KEY, DATE_TIME_FORMATTER.print(date))
      .put(ALL_DAY_KEY, allDay)
      .put(OPEN_KEY, open)
      .put(OPENING_HOUR_KEY, openingHourToJsonArray());
  }
}
