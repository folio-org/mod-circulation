package api.support.builders;

import io.vertx.core.json.JsonObject;
import java.util.List;

public class SearchBuilder extends JsonBuilder implements Builder {
  private final int totalRecords;
  private final JsonObject instance;

  public SearchBuilder(JsonObject instance) {
    this.totalRecords = 1;
    this.instance = instance;
  }

  @Override
  public JsonObject create() {
    final JsonObject search = new JsonObject();

    put(search, "totalRecords", totalRecords);
    put(search, "instances", List.of(instance));

    return search;
  }

  public SearchBuilder withItems(List<JsonObject> items) {
    put(instance, "items", items);
    return new SearchBuilder(instance);
  }
}
