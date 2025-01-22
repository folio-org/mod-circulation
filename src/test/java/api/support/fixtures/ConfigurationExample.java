package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import api.support.builders.ConfigRecordBuilder;
import api.support.builders.PrintHoldRequestConfigurationBuilder;
import io.vertx.core.json.JsonObject;

public class ConfigurationExample {
  private static final String DEFAULT_TIME_ZONE_MODULE_NAME = "ORG";
  private static final String DEFAULT_TIME_ZONE_CONFIG_NAME = "localeSettings";
  private static final String US_LOCALE = "en-US";

  private static final String DEFAULT_NOTIFICATION_SCHEDULER_MODULE_NAME = "NOTIFICATION_SCHEDULER";
  private static final String DEFAULT_NOTIFICATION_SCHEDULER_CONFIG_NAME = "noticesLimit";

  private ConfigurationExample() { }

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
    return new ConfigRecordBuilder(DEFAULT_TIME_ZONE_MODULE_NAME, DEFAULT_TIME_ZONE_CONFIG_NAME,
      combinedTimeZoneConfig(timezone).encodePrettily());
  }

  public static ConfigRecordBuilder schedulerNoticesLimitConfiguration(String limit){
    return new ConfigRecordBuilder(DEFAULT_NOTIFICATION_SCHEDULER_MODULE_NAME,
      DEFAULT_NOTIFICATION_SCHEDULER_CONFIG_NAME, limit);
  }

  public static ConfigRecordBuilder setPrintHoldRequestsEnabled(boolean enabled) {
    return new ConfigRecordBuilder("SETTINGS", "PRINT_HOLD_REQUESTS",
      new PrintHoldRequestConfigurationBuilder()
        .withPrintHoldRequestsEnabled(enabled)
        .create()
        .encodePrettily());
  }

  private static JsonObject combinedTimeZoneConfig(String timezone) {
    final JsonObject encodedValue = new JsonObject();
    write(encodedValue, "locale", US_LOCALE);
    write(encodedValue, "timezone", timezone);
    return encodedValue;
  }

}
