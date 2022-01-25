package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;
import lombok.Value;

@Value
public class Publication {
  String publisher;
  String place;
  String dateOfPublication;
  String role;

  public JsonObject toJson() {
    final var representation = new JsonObject();

    write(representation, "publisher", publisher);
    write(representation, "place", place);
    write(representation, "dateOfPublication", dateOfPublication);
    write(representation, "role", role);

    return representation;
  }
}
