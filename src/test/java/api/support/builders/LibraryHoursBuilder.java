package api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collector;

public class LibraryHoursBuilder extends JsonBuilder implements Builder {

  private static final String OPENING_PERIODS_KEY = "openingPeriods";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private JsonObject representation;

  public LibraryHoursBuilder(List<CalendarBuilder> openingPeriods) {
    this.representation = new JsonObject()
      .put(TOTAL_RECORDS_KEY, openingPeriods.size())
      .put(OPENING_PERIODS_KEY, openingPeriodsToJsonArray(openingPeriods));
  }

  private JsonArray openingPeriodsToJsonArray(List<CalendarBuilder> openingPeriods) {
    return openingPeriods.stream()
      .map(CalendarBuilder::create)
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add));
  }

  @Override
  public JsonObject create() {
    return this.representation;
  }

  @Override
  public String toString() {
    return this.representation.toString();
  }
}
