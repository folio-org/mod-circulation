package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import org.folio.circulation.domain.CallNumberComponents;

import io.vertx.core.json.JsonObject;

public class CallNumberComponentsRepresentation {

  private CallNumberComponentsRepresentation() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static JsonObject createCallNumberComponents(CallNumberComponents callNumberComponents) {
    if (callNumberComponents == null) {
      return null;
    }

    JsonObject representation = new JsonObject();

    write(representation, "callNumber", callNumberComponents.getCallNumber());
    write(representation, "callNumberPrefix", callNumberComponents.getCallNumberPrefix());
    write(representation, "callNumberSuffix", callNumberComponents.getCallNumberSuffix());

    return representation;
  }
}
