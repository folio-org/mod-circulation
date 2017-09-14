package org.folio.circulation.api.support;

public class ItemRequestExamples {
  public static ItemRequestBuilder basedUponSmallAngryPlanet() {
    return new ItemRequestBuilder()
      .withTitle("The Long Way to a Small, Angry Planet")
      .withBarcode("036000291452");
  }

  public static ItemRequestBuilder basedUponNod() {
    return new ItemRequestBuilder()
      .withTitle("Nod")
      .withBarcode("565578437802");
  }

  public static ItemRequestBuilder basedUponUprooted() {
    return new ItemRequestBuilder()
      .withTitle("Uprooted")
      .withBarcode("657670342075");
  }

  public static ItemRequestBuilder basedUponTemeraire() {
    return new ItemRequestBuilder()
      .withTitle("Temeraire")
      .withBarcode("232142443432");
  }

  public static ItemRequestBuilder basedUponInterestingTimes() {
    return new ItemRequestBuilder()
      .withTitle("Interesting Times")
      .withBarcode("56454543534");
  }
}
