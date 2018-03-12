package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.InstanceRequestBuilder;

public class InstanceRequestExamples {
  public static InstanceRequestBuilder basedUponSmallAngryPlanet() {
    return create("The Long Way to a Small, Angry Planet")
      .withContributor("Chambers, Becky");
  }

  public static InstanceRequestBuilder basedUponNod() {
    return create("Nod")
      .withContributor("Barnes, Adrian");
  }

  public static InstanceRequestBuilder basedUponUprooted() {
    return create("Uprooted")
      .withContributor("Novik, Naomi");
  }

  public static InstanceRequestBuilder basedUponTemeraire() {
    return create("Temeraire")
      .withContributor("Novik, Naomi");
  }

  public static InstanceRequestBuilder basedUponInterestingTimes() {
    return create("Interesting Times")
      .withContributor("Pratchett, Terry");
  }

  public static InstanceRequestBuilder basedUponDunkirk() {
    return new InstanceRequestBuilder("Dunkirk")
      .withContributor("Nolan, Christopher");
  }

  private static InstanceRequestBuilder create(String title) {
    return new InstanceRequestBuilder(title);
  }

}
