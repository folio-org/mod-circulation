package api.support.fixtures;

import java.util.List;
import java.util.UUID;

import api.support.builders.SettingsBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class SettingsFixture {
  private static final UUID GENERAL_TLR_SETTINGS_ID = UUID.randomUUID();
  private static final UUID REGULAR_TLR_SETTINGS_ID = UUID.randomUUID();

  private final ResourceClient settingsClient;

  public SettingsFixture() {
    this.settingsClient = ResourceClient.forSettingsStorage();
  }

  public void enableCheckoutLockFeature(boolean checkoutFeatureFlag) {
    settingsClient.create(buildCheckoutLockFeatureSettings(checkoutFeatureFlag));
  }

  private SettingsBuilder buildCheckoutLockFeatureSettings(boolean checkoutFeatureFlag) {
    return new SettingsBuilder(UUID.randomUUID(), "mod-circulation", "checkoutLockFeature",
      new JsonObject().put("checkOutLockFeatureEnabled", checkoutFeatureFlag)
        .put("lockTtl", 500)
        .put("retryInterval", 5)
        .put("noOfRetryAttempts", 10)
        .encodePrettily()
    );
  }

  public void enableTlrFeature() {
    createGeneralTlrSettings(true, false);
  }

  public void disableTlrFeature() {
    createGeneralTlrSettings(false, false);
  }

  public void deleteTlrFeatureSettings() {
    settingsClient.delete(GENERAL_TLR_SETTINGS_ID);
    settingsClient.delete(REGULAR_TLR_SETTINGS_ID);
  }

  public void configureTlrFeature(boolean isTlrFeatureEnabled, boolean tlrHoldShouldFollowCirculationRules,
    UUID confirmationTemplateId, UUID cancellationTemplateId, UUID expirationTemplateId) {

    deleteTlrFeatureSettings();
    createGeneralTlrSettings(isTlrFeatureEnabled, tlrHoldShouldFollowCirculationRules);
    createRegularTlrSettings(confirmationTemplateId, cancellationTemplateId, expirationTemplateId);
  }

  private void createGeneralTlrSettings(boolean isTlrFeatureEnabled,
    boolean tlrHoldShouldFollowCirculationRules) {

    JsonObject value = new JsonObject()
      .put("titleLevelRequestsFeatureEnabled", isTlrFeatureEnabled)
      .put("createTitleLevelRequestsByDefault", false)
      .put("tlrHoldShouldFollowCirculationRules", tlrHoldShouldFollowCirculationRules);

    SettingsBuilder builder = new SettingsBuilder(GENERAL_TLR_SETTINGS_ID, "circulation",
      "generalTlr", value);

    settingsClient.create(builder);
  }

  private void createRegularTlrSettings(UUID confirmationTemplateId, UUID cancellationTemplateId,
    UUID expirationTemplateId) {

    JsonObject regularTlrValue = new JsonObject()
      .put("cancellationPatronNoticeTemplateId", cancellationTemplateId)
      .put("confirmationPatronNoticeTemplateId", confirmationTemplateId)
      .put("expirationPatronNoticeTemplateId", expirationTemplateId);

    SettingsBuilder builder = new SettingsBuilder(REGULAR_TLR_SETTINGS_ID, "circulation",
      "regularTlr", regularTlrValue);

    settingsClient.create(builder);
  }

  public List<JsonObject> getAll() {
    return settingsClient.getAll();
  }

}
