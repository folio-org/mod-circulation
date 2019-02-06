package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

public class Weekdays {

  private static final String DAY_KEY = "day";
  private String day;

  Weekdays(JsonObject jsonObject, String key) {
    JsonObject weekDaysJson = jsonObject.getJsonObject(key);
    this.day = StringUtils.defaultIfBlank(weekDaysJson.getString(DAY_KEY), StringUtils.EMPTY);
  }

  private Weekdays(String day) {
    this.day = day;
  }

  public static Weekdays createWeekdays(String day) {
    return new Weekdays(day);
  }

  public String getDay() {
    return day;
  }

  JsonObject toJson() {
    return new JsonObject().put(DAY_KEY, day);
  }
}
