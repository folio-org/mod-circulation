package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class ItemExamples {
  public static JsonObject smallAngryPlanet() {
    return ItemRequest.create(UUID.randomUUID(), UUID.randomUUID(),
      "The Long Way to a Small, Angry Planet", "036000291452");
  }

  public static JsonObject nod() {
    return ItemRequest.create(UUID.randomUUID(), UUID.randomUUID(),
      "Nod", "565578437802");
  }

  private JsonObject uprooted() {
    return ItemRequest.create(UUID.randomUUID(), UUID.randomUUID(),
      "Uprooted", "657670342075");
  }

  private JsonObject temeraire() {
    return ItemRequest.create(UUID.randomUUID(), UUID.randomUUID(),
      "Temeraire", "232142443432");
  }

  private JsonObject interestingTimes() {
    return ItemRequest.create(UUID.randomUUID(), UUID.randomUUID(),
      "Interesting Times", "56454543534");
  }
}
