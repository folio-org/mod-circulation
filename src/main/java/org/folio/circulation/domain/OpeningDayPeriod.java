package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class OpeningDayPeriod {
  private static final String WEEKDAYS_KEY = "weekdays";
  private static final String OPENING_DAY_KEY = "openingDay";

  private Weekdays weekdays;
  private OpeningDay openingDay;

  OpeningDayPeriod(JsonObject jsonObject) {
    this.weekdays = new Weekdays(jsonObject, WEEKDAYS_KEY);
    this.openingDay = new OpeningDay(jsonObject, OPENING_DAY_KEY);
  }

  private OpeningDayPeriod(Weekdays weekdays, OpeningDay openingDay) {
    this.weekdays = weekdays;
    this.openingDay = openingDay;
  }

  public static OpeningDayPeriod createDayPeriod(Weekdays weekdays, OpeningDay openingDay) {
    return new OpeningDayPeriod(weekdays, openingDay);
  }

  public Weekdays getWeekdays() {
    return weekdays;
  }

  public OpeningDay getOpeningDay() {
    return openingDay;
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put(WEEKDAYS_KEY, weekdays.toJson())
      .put(OPENING_DAY_KEY, openingDay.toJson());
  }
}
