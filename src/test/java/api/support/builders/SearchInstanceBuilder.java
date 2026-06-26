package api.support.builders;

import java.util.List;

import io.vertx.core.json.JsonObject;

public class SearchInstanceBuilder extends JsonBuilder implements Builder {

  private final JsonObject searchInstance;

  public SearchInstanceBuilder(JsonObject searchInstance) {
    this.searchInstance = searchInstance;
  }

  @Override
  public JsonObject create() {
    return searchInstance;
  }

  public SearchInstanceBuilder withItems(List<JsonObject> items) {
    put(searchInstance, "items", items);
    return new SearchInstanceBuilder(searchInstance);
  }
}
