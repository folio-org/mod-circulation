package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.InstanceRequestBuilder;

public class InstanceRequestExamples {
  public static InstanceRequestBuilder basedUponSmallAngryPlanet() {
    return create("The Long Way to a Small, Angry Planet");
  }

  public static InstanceRequestBuilder basedUponNod() {
    return create("Nod");
  }

  public static InstanceRequestBuilder basedUponUprooted() {
    return create("Uprooted");
  }

  public static InstanceRequestBuilder basedUponTemeraire() {
    return create("Temeraire");
  }

  public static InstanceRequestBuilder basedUponInterestingTimes() {
    return create("Interesting Times");
  }

  public static InstanceRequestBuilder basedUponDunkirk() {
    return new InstanceRequestBuilder("Dunkirk");
  }

  private static InstanceRequestBuilder create(String title) {
    return new InstanceRequestBuilder(title);
  }

}
