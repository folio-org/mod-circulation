package api.support.fixtures;

import api.support.builders.ConfigurationBuilder;
import api.support.builders.TimeZoneConfigBuilder;

import java.util.Collections;

public class ConfigurationExample {

  private static final String DEFAULT_VAL = "{\"locale\":\"en-US\",\"timezone\":\"UTC\"}";
  private static final String DEFAULT_MOD = "ORG";
  private static final String DEFAULT_NAME = "localeSettings";

  private ConfigurationExample() {
    // not use
  }

  public static String getConfigurations() {
    TimeZoneConfigBuilder config = new TimeZoneConfigBuilder(DEFAULT_MOD, DEFAULT_NAME, DEFAULT_VAL);
    return new ConfigurationBuilder(Collections.singletonList(config)).toString();
  }

}
