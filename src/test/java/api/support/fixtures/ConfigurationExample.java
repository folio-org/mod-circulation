package api.support.fixtures;

import api.support.builders.ConfigRecordBuilder;
import api.support.builders.PrintHoldRequestConfigurationBuilder;

public class ConfigurationExample {

  private static final String DEFAULT_NOTIFICATION_SCHEDULER_MODULE_NAME = "NOTIFICATION_SCHEDULER";
  private static final String DEFAULT_NOTIFICATION_SCHEDULER_CONFIG_NAME = "noticesLimit";

  private ConfigurationExample() { }

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

}
