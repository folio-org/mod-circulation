package api.support.fixtures;

import api.support.http.ResourceClient;

import java.util.UUID;

public class ConfigurationsFixture {
  private final ResourceClient client;
  private UUID printHoldRequestConfigurationEntryId = null;

  public ConfigurationsFixture(ResourceClient client) {
    this.client = client;
  }

  public void configurePrintHoldRequests(boolean printHoldRequestsEnabled) {
    deletePrintHoldRequestConfig();
    printHoldRequestConfigurationEntryId = client.create(
      ConfigurationExample.setPrintHoldRequestsEnabled(printHoldRequestsEnabled)).getId();
  }

  public void deletePrintHoldRequestConfig() {
    if (printHoldRequestConfigurationEntryId != null) {
      client.delete(printHoldRequestConfigurationEntryId);
      printHoldRequestConfigurationEntryId = null;
    }
  }
}
