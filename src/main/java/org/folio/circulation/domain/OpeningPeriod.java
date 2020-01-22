package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

public class OpeningPeriod {
  private static final String OPENING_DAY_KEY = "openingDay";
  private static final String DATE_KEY = "date";

  private final OpeningDay openingDay;
  private final LocalDate date;

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
    this.openingDay = OpeningDay.fromJsonByDefaultKey(jsonObject);
    String dateProperty = jsonObject.getString(DATE_KEY);
    this.date = DateTime.parse(dateProperty).toLocalDate();
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put(OPENING_DAY_KEY, openingDay.toJson())
      .put(DATE_KEY, date.toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC).toString());
  }
}
