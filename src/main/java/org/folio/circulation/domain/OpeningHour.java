package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.LocalTime;


public class OpeningHour {

  private static final String START_TIME_KEY = "startTime";
  private static final String END_TIME_KEY = "endTime";
  private static final String TIME_PATTERN = "HH:mm";

  private LocalTime startTime;
  private LocalTime endTime;

  OpeningHour(JsonObject jsonObject) {
    this.startTime = LocalTime.parse(jsonObject.getString(START_TIME_KEY));
    this.endTime = LocalTime.parse(jsonObject.getString(END_TIME_KEY));
  }

  public OpeningHour(LocalTime startTime, LocalTime endTime) {
    this.startTime = startTime;
    this.endTime = endTime;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  JsonObject toJson() {
    return new JsonObject()
      .put(START_TIME_KEY, startTime.toString(TIME_PATTERN))
      .put(END_TIME_KEY, endTime.toString(TIME_PATTERN));
  }
}
