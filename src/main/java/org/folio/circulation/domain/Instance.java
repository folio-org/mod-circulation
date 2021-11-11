package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Instance {
  private final JsonObject instanceRepresentation;

  public static Instance from(JsonObject representation) {
    return new Instance(representation);
  }

  public boolean isNotFound() {
    return !isFound();
  }

  public boolean isFound() {
    return instanceRepresentation != null;
  }
}
