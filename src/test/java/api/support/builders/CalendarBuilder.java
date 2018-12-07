package api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.Serializable;

public class CalendarBuilder extends JsonBuilder implements Builder {

  private Integer totalRecords;
  private OpeningPeriod openingPeriod;

  public CalendarBuilder(String id, String servicePointId, String name, String startDate, String endDate, String openingDays, Integer totalRecords) {
    this.totalRecords = totalRecords;
    this.openingPeriod = new OpeningPeriod(id, servicePointId, name, startDate, endDate, openingDays);
  }

  @Override
  public JsonObject create() {
    return new JsonObject()
      .put("totalRecords", totalRecords)
      .put("openingPeriods", new JsonArray().add(openingPeriod.getJson()));
  }

  @Override
  public String toString() {
    return new JsonObject()
      .put("totalRecords", totalRecords)
      .put("openingPeriods", new JsonArray().add(openingPeriod.getJson())).toString();
  }

  private class OpeningPeriod implements Serializable {
    private JsonObject request;

    OpeningPeriod(String id, String servicePointId, String name, String startDate, String endDate, String openingDays) {
      this.request = new JsonObject()
        .put("id", id)
        .put("servicePointId", servicePointId)
        .put("name", name)
        .put("startDate", startDate)
        .put("endDate", endDate)
        .put("openingDays", openingDays);
    }

    public JsonObject getJson() {
      return request;
    }
  }
}
