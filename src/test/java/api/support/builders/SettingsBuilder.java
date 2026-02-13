package api.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class SettingsBuilder implements Builder {

  private final JsonObject representation;

  public SettingsBuilder(UUID id, String scope, String key, Object value) {
    this.representation = new JsonObject()
      .put("id", id.toString())
      .put("scope", scope)
      .put("key", key)
      .put("value", value);
  }

  public SettingsBuilder(UUID id, String locale, String timezone, String currency, String numberingSystem) {
    this.representation = new JsonObject()
      .put("id", id.toString())
      .put("locale", locale)
      .put("timezone", timezone)
      .put("currency", currency);

    if (numberingSystem != null) {
      this.representation.put("numberingSystem", numberingSystem);
    }
  }

  public SettingsBuilder(UUID id, String locale, String timezone, String currency) {
    this(id, locale, timezone, currency, "latn");
  }

  @Override
  public JsonObject create() {
    return representation;
  }
}
