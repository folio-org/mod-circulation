package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.InstanceBuilder;

public class InstanceExamples {
  public static InstanceBuilder basedUponSmallAngryPlanet() {
    return create("The Long Way to a Small, Angry Planet");
  }

  public static InstanceBuilder basedUponNod() {
    return create("Nod");
  }

  public static InstanceBuilder basedUponUprooted() {
    return create("Uprooted");
  }

  public static InstanceBuilder basedUponTemeraire() {
    return create("Temeraire");
  }

  public static InstanceBuilder basedUponInterestingTimes() {
    return create("Interesting Times");
  }

  public static InstanceBuilder basedUponDunkirk() {
    return new InstanceBuilder("Dunkirk");
  }

  private static InstanceBuilder create(String title) {
    return new InstanceBuilder(title);
  }

}
