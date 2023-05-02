package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CallNumberComponents;

import io.vertx.core.json.JsonObject;

public final class CallNumberComponentsRepresentation {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private CallNumberComponentsRepresentation() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static JsonObject createCallNumberComponents(CallNumberComponents callNumberComponents) {
    log.debug("createCallNumberComponents:: parameters callNumberComponents={}",
      callNumberComponents);

    if (callNumberComponents == null) {
      log.info("createCallNumberComponents:: callNumberComponents is null");
      return null;
    }

    JsonObject representation = new JsonObject();

    write(representation, "callNumber", callNumberComponents.getCallNumber());
    write(representation, "prefix", callNumberComponents.getPrefix());
    write(representation, "suffix", callNumberComponents.getSuffix());

    log.info("createCallNumberComponents:: result {}", representation::encode);
    return representation;
  }
}
