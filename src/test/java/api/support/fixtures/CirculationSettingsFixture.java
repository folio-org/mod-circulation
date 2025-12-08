package api.support.fixtures;

import static api.support.fixtures.CirculationSettingExamples.generalTlrSettings;
import static api.support.fixtures.CirculationSettingExamples.regularTlrSettings;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.configuration.CirculationSettingName;

import api.support.builders.CirculationSettingBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class CirculationSettingsFixture {

  private final ResourceClient circulationSettingsClient;
  private static final Map<String, UUID> nameToId = new HashMap<>();

  public CirculationSettingsFixture() {
    circulationSettingsClient = ResourceClient.forCirculationSettings();
  }

  public IndividualResource create(CirculationSettingBuilder builder) {
    String settingName = builder.getName();
    requireNonNull(settingName);
    if (builder.getId() == null) {
      builder = builder.withId(randomUUID());
    }
    delete(settingName);
    IndividualResource createdSetting = circulationSettingsClient.create(builder);
    nameToId.put(settingName, createdSetting.getId());
    return createdSetting;
  }

  public void delete(CirculationSettingName settingName) {
    delete(settingName.getValue());
  }

  public void delete(String settingName) {
    Optional.ofNullable(nameToId.get(settingName))
      .ifPresent(id -> {
        circulationSettingsClient.delete(id);
        nameToId.remove(settingName);
      });
  }

  public IndividualResource setScheduledNoticesProcessingLimit(int limit) {
    return setScheduledNoticesProcessingLimit(String.valueOf(limit));
  }

  public IndividualResource setScheduledNoticesProcessingLimit(String limit) {
    return create(CirculationSettingExamples.scheduledNoticesLimit(limit));
  }

  public IndividualResource setPrintHoldRequests(boolean value) {
    return create(CirculationSettingExamples.printHoldRequests(value));
  }

  public IndividualResource createLoanHistorySettings(LoanHistoryConfigurationBuilder configBuilder) {
    return create(new CirculationSettingBuilder()
      .withId(randomUUID())
      .withName("loan_history")
      .withValue(configBuilder.create()));
  }

  public void enableTlrFeature() {
    createGeneralTlrSettings(true, false);
  }

  public void disableTlrFeature() {
    createGeneralTlrSettings(false, false);
  }

  public void configureTlrFeature(boolean isTlrFeatureEnabled, boolean tlrHoldShouldFollowCirculationRules,
    UUID confirmationTemplateId, UUID cancellationTemplateId, UUID expirationTemplateId) {

    deleteTlrFeatureSettings();
    createGeneralTlrSettings(isTlrFeatureEnabled, tlrHoldShouldFollowCirculationRules);
    createRegularTlrSettings(confirmationTemplateId, cancellationTemplateId, expirationTemplateId);
  }

  public IndividualResource createGeneralTlrSettings(boolean isTlrFeatureEnabled,
    boolean tlrHoldShouldFollowCirculationRules) {

    return create(generalTlrSettings(isTlrFeatureEnabled, tlrHoldShouldFollowCirculationRules));
  }

  public IndividualResource createRegularTlrSettings(UUID confirmationTemplateId,
    UUID cancellationTemplateId, UUID expirationTemplateId) {

    return create(regularTlrSettings(confirmationTemplateId, cancellationTemplateId, expirationTemplateId));
  }

  public void deleteTlrFeatureSettings() {
    delete("generalTlr");
    delete("regularTlr");
    delete("TLR");
  }

  public List<JsonObject> getAllSettings() {
    return circulationSettingsClient.getAll();
  }

}
