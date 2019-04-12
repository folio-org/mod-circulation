package api.support;

import org.folio.circulation.domain.OpeningDay;

import io.vertx.core.json.JsonObject;

public class OpeningDayPeriod {
  private static final String OPENING_DAY_KEY = "openingDay";

  private final OpeningDay openingDay;

  private OpeningDayPeriod(OpeningDay openingDay) {
    this.openingDay = openingDay;
  }

  public static OpeningDayPeriod createDayPeriod(OpeningDay openingDay) {
    return new OpeningDayPeriod(openingDay);
  }

  public OpeningDay getOpeningDay() {
    return openingDay;
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put(OPENING_DAY_KEY, openingDay.toJson());
  }
}
