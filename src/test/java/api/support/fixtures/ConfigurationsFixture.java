package api.support.fixtures;

import java.util.UUID;

import api.support.builders.NoticeConfigurationBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.Period;

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

  public void configureTlrFeature(boolean isTlrFeatureEnabled,  UUID confirmationTemplateId,
    UUID cancellationTemplateId, UUID expirationTemplateId) {

    deleteTlrFeatureConfig();
    tlrConfigurationEntryId = client.create(ConfigurationExample.tlrNoticesConfiguration(
      isTlrFeatureEnabled, confirmationTemplateId, cancellationTemplateId, expirationTemplateId))
      .getId();
  }

  public JsonObject createBeforeDueDateNoticeConfiguration(UUID templateId, Period beforePeriod,
    Period beforeRecurringPeriod, boolean realTime) {

    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withBeforeTiming(beforePeriod)
      .recurring(beforeRecurringPeriod)
      .sendInRealTime(realTime)
      .create();
  }

  public JsonObject createUponAtDueDateNoticeConfiguration(UUID templateId, boolean realTime) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(realTime)
      .create();
  }

  public JsonObject createAfterDueDateNoticeConfiguration(UUID templateId, Period afterPeriod,
    Period afterRecurringPeriod, boolean realTime) {

    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withAfterTiming(afterPeriod)
      .recurring(afterRecurringPeriod)
      .sendInRealTime(realTime)
      .create();
  }
}
