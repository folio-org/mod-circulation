package api.support.fixtures;

import api.support.builders.CirculationSettingBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;

public class CirculationSettingsFixture {

  private final ResourceClient circulationSettingsClient;

  public CirculationSettingsFixture() {
    circulationSettingsClient = ResourceClient.forCirculationSettings();
  }

  public IndividualResource create(CirculationSettingBuilder builder) {
    return circulationSettingsClient.create(builder);
  }
}
