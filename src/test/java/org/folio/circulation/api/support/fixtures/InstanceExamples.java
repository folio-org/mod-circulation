package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.InstanceBuilder;

public class InstanceExamples {
  public static InstanceBuilder basedUponSmallAngryPlanet() {
    return new InstanceBuilder("The Long Way to a Small, Angry Planet")
      .withContributor("Chambers, Becky");
  }

  public static InstanceBuilder basedUponNod() {
    return new InstanceBuilder("Nod")
      .withContributor("Barnes, Adrian");
  }

  public static InstanceBuilder basedUponUprooted() {
    return new InstanceBuilder("Uprooted")
      .withContributor("Novik, Naomi");
  }

  public static InstanceBuilder basedUponTemeraire() {
    return new InstanceBuilder("Temeraire")
      .withContributor("Novik, Naomi");
  }

  public static InstanceBuilder basedUponInterestingTimes() {
    return new InstanceBuilder("Interesting Times")
      .withContributor("Pratchett, Terry");
  }

  public static InstanceBuilder basedUponDunkirk() {
    return new InstanceBuilder("Dunkirk")
      .withContributor("Nolan, Christopher");
  }
}
