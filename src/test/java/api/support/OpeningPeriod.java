package api.support;

import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;

import org.folio.circulation.domain.OpeningDay;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import io.vertx.core.json.JsonObject;

public class OpeningPeriod {
  private static final String OPENING_DAY_KEY = "openingDay";
  private static final String DATE_KEY = "date";

  private final OpeningDay openingDay;
  private final LocalDate date;

  public OpeningPeriod(LocalDate date, OpeningDay openingDay) {
    this.openingDay = openingDay;
    this.date = date;
  }

  public static OpeningPeriod from(JsonObject jsonObject) {
    return new OpeningPeriod(DateTime.parse(jsonObject.getString(DATE_KEY)).toLocalDate(),
      OpeningDay.fromJsonByDefaultKey(jsonObject));
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put(OPENING_DAY_KEY, openingDay.toJson())
      .put(DATE_KEY, atStartOfDay(date, DateTimeZone.UTC).toString());
  }

  public OpeningDay getOpeningDay() {
    return openingDay;
  }

  public LocalDate getDate() {
    return date;
  }
}
