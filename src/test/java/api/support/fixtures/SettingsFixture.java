package api.support.fixtures;

import api.support.builders.SettingsBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class SettingsFixture {

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
}
