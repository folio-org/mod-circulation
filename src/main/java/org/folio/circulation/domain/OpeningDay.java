package org.folio.circulation.domain;

import static java.util.Objects.requireNonNull;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getJodaLocalDateProperty;
import static org.joda.time.LocalTime.MIDNIGHT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class OpeningDay {
  public static OpeningDay createClosedDay() {
    return createOpeningDay(Collections.emptyList(), null, true, false);
  }

  private static final String DATE_KEY = "date";
  private static final String ALL_DAY_KEY = "allDay";
  private static final String OPEN_KEY = "open";
  private static final String OPENING_HOUR_KEY = "openingHour";
  private static final String OPENING_DAY_KEY = "openingDay";
  private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
    DateTimeFormat.forPattern(DATE_TIME_FORMAT).withZoneUTC();

  private final List<OpeningHour> openingHour;
  private final LocalDate date;
  private final boolean allDay;
  private final boolean open;
  private final DateTime dayWithTimeZone;

  public static OpeningDay fromJsonByDefaultKey(JsonObject jsonObject) {
    JsonObject openingDayJson = jsonObject.getJsonObject(OPENING_DAY_KEY);

    requireNonNull(openingDayJson, "Json object cannot be null");

    return new OpeningDay(fillOpeningDay(openingDayJson),
      getJodaLocalDateProperty(openingDayJson, DATE_KEY),
      getBooleanProperty(openingDayJson, ALL_DAY_KEY),
      getBooleanProperty(openingDayJson, OPEN_KEY), null);
  }

  public static OpeningDay fromOpeningPeriodJson(JsonObject openingPeriod, DateTimeZone zone) {
    JsonObject openingDayJson = openingPeriod.getJsonObject(OPENING_DAY_KEY);

    return createOpeningDay(fillOpeningDay(openingDayJson),
      getJodaLocalDateProperty(openingDayJson, DATE_KEY),
      getBooleanProperty(openingDayJson, ALL_DAY_KEY),
      getBooleanProperty(openingDayJson, OPEN_KEY), zone);
  }

  public static OpeningDay createOpeningDay(List<OpeningHour> openingHour, LocalDate date,
    boolean allDay, boolean open) {

    return new OpeningDay(openingHour, date, allDay, open, null);
  }

  public static OpeningDay createOpeningDay(List<OpeningHour> openingHour,
    LocalDate date, boolean allDay, boolean open, DateTimeZone zone) {

    return new OpeningDay(openingHour, date, allDay, open, date.toDateTime(MIDNIGHT, zone));
  }

  public OpeningDay(List<OpeningHour> openingHour, LocalDate date,
    boolean allDay, boolean open, DateTime dateWithTimeZone) {

    this.openingHour = openingHour;
    this.date = date;
    this.allDay = allDay;
    this.open = open;
    this.dayWithTimeZone = dateWithTimeZone;
  }

  public LocalDate getDate() {
    return date;
  }

  public DateTime getDayWithTimeZone() {
    return dayWithTimeZone;
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
    DateTime dateTime = date.toDateTime(MIDNIGHT, DateTimeZone.UTC);
    return new JsonObject()
      .put(DATE_KEY, DATE_TIME_FORMATTER.print(dateTime))
      .put(ALL_DAY_KEY, allDay)
      .put(OPEN_KEY, open)
      .put(OPENING_HOUR_KEY, openingHourToJsonArray());
  }

  private static List<OpeningHour> fillOpeningDay(JsonObject representation) {
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
}
