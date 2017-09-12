package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class ItemRequestExamples {
  public static JsonObject smallAngryPlanet() {
    return smallAngryPlanet("036000291452");
  }

  public static JsonObject smallAngryPlanetNoBarcode() {
    return smallAngryPlanet(null);
  }

  public static JsonObject smallAngryPlanet(String barcode) {
    return ItemRequest.create(UUID.randomUUID(),
      "The Long Way to a Small, Angry Planet", barcode);
  }

  public static JsonObject nod() {
    return nod("565578437802");
  }

  public static JsonObject nod(String barcode) {
    return ItemRequest.create(UUID.randomUUID(), "Nod", barcode);
  }

  public static JsonObject uprooted() {
    return uprooted("657670342075");
  }

  public static JsonObject uprooted(String barcode) {
    return ItemRequest.create(UUID.randomUUID(), "Uprooted", barcode);
  }

  public static JsonObject temeraire() {
    return temeraire("232142443432");
  }

  public static JsonObject temeraire(String barcode) {
    return ItemRequest.create(UUID.randomUUID(), "Temeraire", barcode);
  }

  public static JsonObject interestingTimes() {
    return interestingTimes("56454543534");
  }

  public static JsonObject interestingTimes(String barcode) {
    return ItemRequest.create(UUID.randomUUID(), "Interesting Times", barcode);
  }
}
