package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.InstanceRequestBuilder;

public class InstanceRequestExamples {
  public static InstanceRequestBuilder smallAngryPlanet() {
    return create("The Long Way to a Small, Angry Planet", "Chambers, Becky");
  }

  public static InstanceRequestBuilder nod() {
    return create("Nod", "Barnes, Adrian");
  }

  public static InstanceRequestBuilder uprooted() {
    return create("Uprooted", "Novik, Naomi");
  }

  public static InstanceRequestBuilder temeraire() {
    return create("Temeraire", "Novik, Naomi");
  }

  public static InstanceRequestBuilder interestingTimes() {
    return create("Interesting Times", "Pratchett, Terry");
  }

  private static InstanceRequestBuilder create(
    String title,
    String creator) {

    return new InstanceRequestBuilder(
      title,
      creator);
  }
}
