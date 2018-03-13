package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.InstanceBuilder;

public class InstanceExamples {
  public static InstanceBuilder basedUponSmallAngryPlanet() {
    return create("The Long Way to a Small, Angry Planet")
      .withContributor("Chambers, Becky");
  }

  public static InstanceBuilder basedUponNod() {
    return create("Nod")
      .withContributor("Barnes, Adrian");
  }

  public static InstanceBuilder basedUponUprooted() {
    return create("Uprooted")
      .withContributor("Novik, Naomi");
  }

  public static InstanceBuilder basedUponTemeraire() {
    return create("Temeraire")
      .withContributor("Novik, Naomi");
  }

  public static InstanceBuilder basedUponInterestingTimes() {
    return create("Interesting Times")
      .withContributor("Pratchett, Terry");
  }

  public static InstanceBuilder basedUponDunkirk() {
    return new InstanceBuilder("Dunkirk")
      .withContributor("Nolan, Christopher");
  }

  private static InstanceBuilder create(String title) {
    return new InstanceBuilder(title);
  }

}
