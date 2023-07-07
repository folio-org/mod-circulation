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
    deleteTlrFeatureConfig();
    tlrConfigurationEntryId = client.create(ConfigurationExample.tlrFeatureEnabled()).getId();
  }

  public void disableTlrFeature() {
    deleteTlrFeatureConfig();
    tlrConfigurationEntryId = client.create(ConfigurationExample.tlrFeatureDisabled()).getId();
  }

  public void deleteTlrFeatureConfig() {
    if (tlrConfigurationEntryId != null) {
      client.delete(tlrConfigurationEntryId);
      tlrConfigurationEntryId = null;
    }
  }

  public void configureTlrFeature(boolean isTlrFeatureEnabled, boolean tlrHoldShouldFollowCirculationRules,
    UUID confirmationTemplateId, UUID cancellationTemplateId, UUID expirationTemplateId) {

    deleteTlrFeatureConfig();
    tlrConfigurationEntryId = client.create(ConfigurationExample.tlrFeatureConfiguration(
      isTlrFeatureEnabled, tlrHoldShouldFollowCirculationRules, confirmationTemplateId,
        cancellationTemplateId, expirationTemplateId))
      .getId();
  }
}
