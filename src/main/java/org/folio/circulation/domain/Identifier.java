package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;
import lombok.Value;

@Value
public class Identifier {
  String typeId;
  String value;

  public JsonObject toJson() {
    final var representation = new JsonObject();

    write(representation, "identifierTypeId", typeId);
    write(representation, "value", value);

    return representation;
  }
}
