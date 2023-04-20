package org.folio.circulation.domain.override;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;

public class RenewalBlockOverride extends BlockOverride {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private RenewalBlockOverride(boolean requested) {
    super(requested);
  }

  public static RenewalBlockOverride from(JsonObject representation) {
    log.debug("from:: parameters representation: {}", representation);

    return new RenewalBlockOverride(representation != null);
 }
}
