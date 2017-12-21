package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.InstanceRequestBuilder;

public class InstanceRequestExamples {
  public static InstanceRequestBuilder smallAngryPlanet() {
    return create("The Long Way to a Small, Angry Planet");
  }

  public static InstanceRequestBuilder nod() {
    return create("Nod");
  }

  public static InstanceRequestBuilder uprooted() {
    return create("Uprooted");
  }

  public static InstanceRequestBuilder temeraire() {
    return create("Temeraire");
  }

  public static InstanceRequestBuilder interestingTimes() {
    return create("Interesting Times");
  }

  private static InstanceRequestBuilder create(String title) {
    return new InstanceRequestBuilder(title);
  }
}
