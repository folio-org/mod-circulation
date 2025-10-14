package api.support.fixtures;

import java.util.UUID;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class CirculationSettingFixture {
  private final ResourceClient client;
  private UUID printEventLogFeatureConfigurationEntryId = null;

  public CirculationSettingFixture(ResourceClient circulationSettingsClient) {
    this.client = circulationSettingsClient;
  }

  public void configurePrintEventLogFeature(Object enablePrintLog) {
    deletePrintEventLogFeatureConfig();
    printEventLogFeatureConfigurationEntryId = client.create(
      setPrintEventLogFeatureEnabled(enablePrintLog)).getId();
  }

  public void deletePrintEventLogFeatureConfig() {
    if (printEventLogFeatureConfigurationEntryId != null) {
      client.delete(printEventLogFeatureConfigurationEntryId);
      printEventLogFeatureConfigurationEntryId = null;
    }
  }

  private JsonObject setPrintEventLogFeatureEnabled(Object enablePrintLog) {
    return new JsonObject()
      .put("id", UUID.randomUUID())
      .put("name", "printEventLogFeature")
      .put("value", new JsonObject().put("enablePrintLog", enablePrintLog));
  }
}

