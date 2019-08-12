package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import api.support.builders.ConfigRecordBuilder;
import io.vertx.core.json.JsonObject;

public class ConfigurationExample {

  private static final String DEFAULT_TIME_ZONE_MODULE_NAME = "ORG";
  private static final String DEFAULT_TIME_ZONE_CONFIG_NAME = "localeSettings";
  private static final String US_LOCALE = "en-US";

  private static final String DEFAULT_NOTIFICATION_SCHEDULER_MODULE_NAME = "NOTIFICATION_SCHEDULER";
  private static final String DEFAULT_NOTIFICATION_SCHEDULER_CONFIG_NAME = "noticesLimit";

  private ConfigurationExample() {
    // not use
  }

  public static ConfigRecordBuilder utcTimezoneConfiguration() {
    return getLocaleAndTimeZoneConfiguration("UTC");
  }

  public static ConfigRecordBuilder newYorkTimezoneConfiguration() {
    return getLocaleAndTimeZoneConfiguration("America/New_York");
  }

  private static ConfigRecordBuilder getLocaleAndTimeZoneConfiguration(String timezone) {
    return new ConfigRecordBuilder(DEFAULT_TIME_ZONE_MODULE_NAME, DEFAULT_TIME_ZONE_CONFIG_NAME,
      combinedTimeZoneConfig(timezone).encodePrettily());
  }

  public static ConfigRecordBuilder schedulerNoticesLimitConfiguration(String limit){
    return new ConfigRecordBuilder(DEFAULT_NOTIFICATION_SCHEDULER_MODULE_NAME,
      DEFAULT_NOTIFICATION_SCHEDULER_CONFIG_NAME, limit);
  }

  private static JsonObject combinedTimeZoneConfig(String timezone) {
    final JsonObject encodedValue = new JsonObject();
    write(encodedValue, "locale", US_LOCALE);
    write(encodedValue, "timezone", timezone);
    return encodedValue;
  }

}
