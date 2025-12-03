package api.support.fixtures;

import api.support.builders.ConfigRecordBuilder;
import api.support.builders.PrintHoldRequestConfigurationBuilder;

public class ConfigurationExample {

  private ConfigurationExample() { }

  public static ConfigRecordBuilder setPrintHoldRequestsEnabled(boolean enabled) {
    return new ConfigRecordBuilder("SETTINGS", "PRINT_HOLD_REQUESTS",
      new PrintHoldRequestConfigurationBuilder()
        .withPrintHoldRequestsEnabled(enabled)
        .create()
        .encodePrettily());
  }

}
