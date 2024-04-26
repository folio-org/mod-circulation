package api.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class SettingsBuilder implements Builder {

  private final JsonObject representation;

  public SettingsBuilder(UUID id, String scope, String key, Object value) {
    this.representation = new JsonObject()
      .put("id", id)
      .put("scope", scope)
      .put("key", key)
      .put("value", value);
  }

  @Override
  public JsonObject create() {
    return representation;
  }
}
