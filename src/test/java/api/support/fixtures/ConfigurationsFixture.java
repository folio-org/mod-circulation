package api.support.fixtures;

import java.util.UUID;

import api.support.http.ResourceClient;

public class ConfigurationsFixture {
  private final ResourceClient client;
  private UUID tlrConfigurationEntryId = null;

  public ConfigurationsFixture(ResourceClient client) {
    this.client = client;
  }

  public void enableTlrFeature() {
    tlrConfigurationEntryId = client.create(ConfigurationExample.tlrFeatureEnabled()).getId();
  }

  public void disableTlrFeature() {
    if (tlrConfigurationEntryId != null) {
      client.delete(tlrConfigurationEntryId);
      tlrConfigurationEntryId = null;
    }
  }
}
