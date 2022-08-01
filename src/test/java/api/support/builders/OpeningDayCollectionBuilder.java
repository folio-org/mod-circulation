package api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.stream.Collector;
import org.folio.circulation.domain.OpeningDay;

public class OpeningDayCollectionBuilder
  extends JsonBuilder
  implements Builder {

  private static final String DATES_KEY = "dates";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private JsonObject representation;

  public OpeningDayCollectionBuilder(List<OpeningDay> openingDays) {
    this.representation =
      new JsonObject()
        .put(TOTAL_RECORDS_KEY, openingDays.size())
        .put(DATES_KEY, openingDaysToJsonArray(openingDays));
  }

  private JsonArray openingDaysToJsonArray(List<OpeningDay> openingDays) {
    return openingDays
      .stream()
      .map(OpeningDay::toJson)
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add));
  }

  @Override
  public JsonObject create() {
    return this.representation;
  }
}
