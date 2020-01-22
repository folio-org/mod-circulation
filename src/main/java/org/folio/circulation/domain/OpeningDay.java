package org.folio.circulation.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static java.util.Objects.requireNonNull;

public class OpeningDay {
  static OpeningDay createClosedDay() {
    return createOpeningDay(Collections.emptyList(), null, true, false);
  }

  private static final String DATE_KEY = "date";
  private static final String ALL_DAY_KEY = "allDay";
  private static final String OPEN_KEY = "open";
  private static final String OPENING_HOUR_KEY = "openingHour";
  private static final String OPENING_DAY_KEY = "openingDay";
  private static final String EXCEPTIONAL_KEY = "exceptional";
  private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
    DateTimeFormat.forPattern(DATE_TIME_FORMAT).withZoneUTC();

  private List<OpeningHour> openingHour;
  private LocalDate date;
  private boolean allDay;
  private boolean open;
  private boolean exceptional;

  OpeningDay(JsonObject openingDayJson) {
    requireNonNull(openingDayJson, "Json object cannot be null");
    this.allDay = openingDayJson.getBoolean(ALL_DAY_KEY, false);
    this.open = openingDayJson.getBoolean(OPEN_KEY, false);
    this.openingHour = fillOpeningDay(openingDayJson);
    String dateProperty = openingDayJson.getString(DATE_KEY);
    if (dateProperty != null) {
      this.date = LocalDate.parse(dateProperty, DATE_TIME_FORMATTER);
    }
    this.exceptional = openingDayJson.getBoolean(EXCEPTIONAL_KEY, false);
  }

  public static OpeningDay fromJsonByKey(JsonObject jsonObject, String key) {
    return new OpeningDay(jsonObject.getJsonObject(key));
  }

  public static OpeningDay fromJsonByDefaultKey(JsonObject jsonObject) {
    return fromJsonByKey(jsonObject, OPENING_DAY_KEY);
  }

  private OpeningDay(List<OpeningHour> openingHour, LocalDate date, boolean allDay, boolean open) {
    this.openingHour = openingHour;
    this.date = date;
    this.allDay = allDay;
    this.open = open;
  }

  public OpeningDay(List<OpeningHour> openingHour, boolean allDay, boolean open, boolean exceptional) {
    this.openingHour = openingHour;
    this.allDay = allDay;
    this.open = open;
    this.exceptional = exceptional;
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

  public JsonObject toJson() {
    JsonObject json = new JsonObject()
      .put(ALL_DAY_KEY, allDay)
      .put(OPEN_KEY, open)
      .put(EXCEPTIONAL_KEY, exceptional)
      .put(OPENING_HOUR_KEY, openingHourToJsonArray());

    if (date != null) {
      DateTime dateTime = date.toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC);
      json.put(DATE_KEY, DATE_TIME_FORMATTER.print(dateTime));
    }
    return json;
  }
}
