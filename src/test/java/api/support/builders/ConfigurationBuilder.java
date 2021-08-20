package api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collector;

public class ConfigurationBuilder extends JsonBuilder implements Builder {

  private static final String CONFIGS_KEY = "configs";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";

  private JsonObject representation;

  public ConfigurationBuilder(List<ConfigRecordBuilder> configurations) {
    this.representation = new JsonObject()
      .put(TOTAL_RECORDS_KEY, configurations.size())
      .put(CONFIGS_KEY, openingDaysToJsonArray(configurations));
  }

  private JsonArray openingDaysToJsonArray(List<ConfigRecordBuilder> configurations) {
    return configurations.stream()
      .map(ConfigRecordBuilder::create)
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
