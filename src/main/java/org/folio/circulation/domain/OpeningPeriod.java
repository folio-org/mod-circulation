package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class OpeningPeriod {
  private static final String OPENING_DAY_KEY = "openingDay";
  private static final String DATE_KEY = "date";
  private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
    DateTimeFormat.forPattern(DATE_TIME_FORMAT).withZoneUTC();

  private OpeningDay openingDay;
  private LocalDate date;

  public OpeningDay getOpeningDay() {
    return openingDay;
  }

  public LocalDate getDate() {
    return date;
  }

  public OpeningPeriod(LocalDate date, OpeningDay openingDay) {
    this.openingDay = openingDay;
    this.date = date;
  }

  public OpeningPeriod(JsonObject jsonObject) {
    JsonObject openingDayJson = jsonObject.getJsonObject(OPENING_DAY_KEY);
    this.openingDay = new OpeningDay(openingDayJson);

    String dateProperty = jsonObject.getString(DATE_KEY);
    if (dateProperty != null) {
      this.date = LocalDate.parse(dateProperty, DATE_TIME_FORMATTER);
    }
  }

  public JsonObject toJson() {
    DateTime dateTime = date.toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC);
    return new JsonObject()
      .put(OPENING_DAY_KEY, openingDay.toJson())
      .put(DATE_KEY, DATE_TIME_FORMATTER.print(dateTime));
  }
}
