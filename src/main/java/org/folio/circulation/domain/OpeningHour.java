package org.folio.circulation.domain;

import static org.folio.circulation.support.utils.DateFormatUtil.TIME_MINUTES;

import io.vertx.core.json.JsonObject;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OpeningHour {

  private static final String START_TIME_KEY = "startTime";
  private static final String END_TIME_KEY = "endTime";

  private LocalTime startTime;
  private LocalTime endTime;

  public OpeningHour(JsonObject jsonObject) {
    this(
      LocalTime.parse(jsonObject.getString(START_TIME_KEY)),
      LocalTime.parse(jsonObject.getString(END_TIME_KEY))
    );
  }

  JsonObject toJson() {
    return new JsonObject()
      .put(START_TIME_KEY, startTime.format(TIME_MINUTES))
      .put(END_TIME_KEY, endTime.format(TIME_MINUTES));
  }
}
