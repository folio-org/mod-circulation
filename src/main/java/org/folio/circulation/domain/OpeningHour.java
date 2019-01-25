package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

public class OpeningHour {

  private static final String START_TIME_KEY = "startTime";
  private static final String END_TIME_KEY = "endTime";

  private String startTime;
  private String endTime;

  OpeningHour(JsonObject jsonObject) {
    this.startTime = StringUtils.defaultIfBlank(jsonObject.getString(START_TIME_KEY), StringUtils.EMPTY);
    this.endTime = StringUtils.defaultIfBlank(jsonObject.getString(END_TIME_KEY), StringUtils.EMPTY);
  }

  private OpeningHour(String startTime, String endTime) {
    this.startTime = startTime;
    this.endTime = endTime;
  }

  public String getStartTime() {
    return startTime;
  }

  public String getEndTime() {
    return endTime;
  }

  public static OpeningHour createOpeningHour(String startTime, String endTime) {
    return new OpeningHour(startTime, endTime);
  }

  JsonObject toJson() {
    return new JsonObject()
      .put(START_TIME_KEY, startTime)
      .put(END_TIME_KEY, endTime);
  }
}
