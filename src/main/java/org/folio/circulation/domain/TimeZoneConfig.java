package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class TimeZoneConfig {

  private static final String MODULE_KEY = "module";
  private static final String CONFIG_NAME_KEY = "configName";
  private static final String VALUE_KEY = "value";

  private String module;
  private String configName;
  private String value;

  TimeZoneConfig(JsonObject jsonObject) {
    module = jsonObject.getString(MODULE_KEY);
    configName = jsonObject.getString(CONFIG_NAME_KEY);
    value = jsonObject.getString(VALUE_KEY);
  }

  public String getModule() {
    return module;
  }

  public String getConfigName() {
    return configName;
  }

  public String getValue() {
    return value;
  }
}
