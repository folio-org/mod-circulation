package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.ItemRequestBuilder;

public class ItemRequestExamples {
  public static ItemRequestBuilder basedUponSmallAngryPlanet() {
    return new ItemRequestBuilder()
      .withBarcode("036000291452");
  }

  public static ItemRequestBuilder basedUponNod() {
    return new ItemRequestBuilder()
      .withBarcode("565578437802");
  }

  public static ItemRequestBuilder basedUponUprooted() {
    return new ItemRequestBuilder()
      .withBarcode("657670342075");
  }

  public static ItemRequestBuilder basedUponTemeraire() {
    return new ItemRequestBuilder()
      .withBarcode("232142443432");
  }

  public static ItemRequestBuilder basedUponInterestingTimes() {
    return new ItemRequestBuilder()
      .withBarcode("56454543534");
  }
}
