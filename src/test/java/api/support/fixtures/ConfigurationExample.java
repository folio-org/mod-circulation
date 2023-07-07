package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.UUID;

import api.support.builders.ConfigRecordBuilder;
import api.support.builders.TlrSettingsConfigurationBuilder;
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

  public static ConfigRecordBuilder tlrFeatureEnabled() {
    return new ConfigRecordBuilder("SETTINGS", "TLR",
      new TlrSettingsConfigurationBuilder()
        .withTitleLevelRequestsFeatureEnabled(true)
        .create()
        .encodePrettily());
  }

  public static ConfigRecordBuilder tlrFeatureDisabled() {
    return new ConfigRecordBuilder("SETTINGS", "TLR",
      new TlrSettingsConfigurationBuilder()
        .withTitleLevelRequestsFeatureEnabled(false)
        .create()
        .encodePrettily());
  }

  public static ConfigRecordBuilder tlrFeatureConfiguration(boolean isTlrEnabled,
    boolean holdShouldFollowCirculationRules, UUID confirmationTemplateId,
    UUID cancellationTemplateId, UUID expirationTemplateId) {

    return new ConfigRecordBuilder("SETTINGS", "TLR",
      new TlrSettingsConfigurationBuilder()
        .withTitleLevelRequestsFeatureEnabled(isTlrEnabled)
        .withTlrHoldShouldFollowCirculationRules(holdShouldFollowCirculationRules)
        .withConfirmationPatronNoticeTemplateId(confirmationTemplateId)
        .withCancellationPatronNoticeTemplateId(cancellationTemplateId)
        .withExpirationPatronNoticeTemplateId(expirationTemplateId)
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
