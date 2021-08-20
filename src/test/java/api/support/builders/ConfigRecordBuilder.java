package api.support.builders;

import io.vertx.core.json.JsonObject;

public class ConfigRecordBuilder extends JsonBuilder implements Builder {

  private static final String MODULE_KEY = "module";
  private static final String CONFIG_NAME_KEY = "configName";
  private static final String VALUE_KEY = "value";

  private JsonObject representation;

  public ConfigRecordBuilder(String module, String configName, String value) {
    this.representation = new JsonObject()
      .put(MODULE_KEY, module)
      .put(CONFIG_NAME_KEY, configName)
      .put(VALUE_KEY, value);
  }

  public ConfigRecordBuilder(String value) {
    this.representation = new JsonObject()
      .put(VALUE_KEY, value);
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
