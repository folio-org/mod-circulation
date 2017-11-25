package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.InstanceRequestBuilder;

public class InstanceRequestExamples {
  public static InstanceRequestBuilder smallAngryPlanet() {
    return new InstanceRequestBuilder(
      "The Long Way to a Small, Angry Planet",
      "Chambers, Becky");
  }
}
