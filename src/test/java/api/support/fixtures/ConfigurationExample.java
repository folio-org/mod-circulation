package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import api.support.builders.TimeZoneConfigBuilder;
import io.vertx.core.json.JsonObject;

public class ConfigurationExample {

  private static final String DEFAULT_MOD = "ORG";
  private static final String DEFAULT_NAME = "localeSettings";
  private static final String US_LOCALE = "en-US";

  private ConfigurationExample() {
    // not use
  }

  public static TimeZoneConfigBuilder utcTimezoneConfiguration() {
    return getLocaleAndTimeZoneConfiguration("UTC");
  }

  public static TimeZoneConfigBuilder newYorkTimezoneConfiguration() {
    return getLocaleAndTimeZoneConfiguration("America/New_York");
  }

  private static TimeZoneConfigBuilder getLocaleAndTimeZoneConfiguration(String timezone) {
    return new TimeZoneConfigBuilder(DEFAULT_MOD, DEFAULT_NAME,
      combinedConfiguration(timezone).encodePrettily());
  }

  private static JsonObject combinedConfiguration(String timezone) {
    final JsonObject encodedValue = new JsonObject();
    write(encodedValue, "locale", US_LOCALE);
    write(encodedValue, "timezone", timezone);
    return encodedValue;
  }

}
