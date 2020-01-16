package api.support;

import org.folio.circulation.domain.OpeningDay;

import io.vertx.core.json.JsonObject;
import org.joda.time.LocalDate;

public class OpeningDayPeriod {
  private static final String OPENING_DAY_KEY = "openingDay";
  private static final String DATE_KEY = "openingDay";

  private final OpeningDay openingDay;
  private LocalDate date;

  private OpeningDayPeriod(OpeningDay openingDay) {
    this.openingDay = openingDay;
  }

  private OpeningDayPeriod(OpeningDay openingDay, LocalDate date) {
    this.openingDay = openingDay;
    this.date = date;
  }

  public static OpeningDayPeriod createDayPeriod(OpeningDay openingDay) {
    return new OpeningDayPeriod(openingDay);
  }

  public static OpeningDayPeriod createDayPeriod(OpeningDay openingDay, LocalDate date) {
    return new OpeningDayPeriod(openingDay, date);
  }

  public OpeningDay getOpeningDay() {
    return openingDay;
  }

  public LocalDate getDate() {
    return date;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject()
      .put(OPENING_DAY_KEY, openingDay.toJson());

    if (date != null) {
      json.put(DATE_KEY, date);
    }

    return json;
  }
}
