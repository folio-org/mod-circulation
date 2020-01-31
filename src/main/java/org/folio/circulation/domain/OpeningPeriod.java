package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

public class OpeningPeriod {
  private static final String DATE_KEY = "date";

  private final OpeningDay openingDay;
  private final LocalDate date;

  public OpeningDay getOpeningDay() {
    return openingDay;
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

}
