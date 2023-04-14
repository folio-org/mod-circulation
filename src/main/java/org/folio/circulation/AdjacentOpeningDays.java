package org.folio.circulation;

import java.util.Arrays;
import java.util.List;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.folio.circulation.domain.OpeningDay;

@Getter
@AllArgsConstructor
public class AdjacentOpeningDays {

  private final OpeningDay previousDay;
  private final OpeningDay requestedDay;
  private final OpeningDay nextDay;

  public static AdjacentOpeningDays createClosedOpeningDays() {
    return new AdjacentOpeningDays(
      OpeningDay.createClosedDay(),
      OpeningDay.createClosedDay(),
      OpeningDay.createClosedDay()
    );
  }

  public List<OpeningDay> toList() {
    return Arrays.asList(this.getPreviousDay(), this.getRequestedDay(), this.getNextDay());
  }

  public List<JsonObject> toJsonList() {
    return Arrays.asList(this.getPreviousDay().toJson(), this.getRequestedDay().toJson(),
      this.getNextDay().toJson());
  }
}
