package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import api.support.builders.ConfigRecordBuilder;
import io.vertx.core.json.JsonObject;

public class ConfigurationExample {

  private static final String DEFAULT_MOD = "ORG";
  private static final String DEFAULT_NAME = "localeSettings";
  private static final String US_LOCALE = "en-US";

  private ConfigurationExample() {
    // not use
  }

  public static ConfigRecordBuilder utcTimezoneConfiguration() {
    return timezoneConfigurationFor("UTC");
  }

  public static ConfigRecordBuilder newYorkTimezoneConfiguration() {
    return timezoneConfigurationFor("America/New_York");
  }

  public static ConfigRecordBuilder timezoneConfigurationFor(String timezone) {
    return getLocaleAndTimeZoneConfiguration(timezone);
  }

  private static ConfigRecordBuilder getLocaleAndTimeZoneConfiguration(String timezone) {
    return new ConfigRecordBuilder(DEFAULT_MOD, DEFAULT_NAME,
      combinedConfiguration(timezone).encodePrettily());
  }

  private static JsonObject combinedConfiguration(String timezone) {
    final JsonObject encodedValue = new JsonObject();
    write(encodedValue, "locale", US_LOCALE);
    write(encodedValue, "timezone", timezone);
    return encodedValue;
  }

}
