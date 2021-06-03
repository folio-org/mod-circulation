package org.folio.circulation.domain.override;

import io.vertx.core.json.JsonObject;

public class RenewalBlockOverride extends BlockOverride {

  private RenewalBlockOverride(boolean requested) {
    super(requested);
  }

  public static RenewalBlockOverride from(JsonObject representation) {
    return new RenewalBlockOverride(representation != null);
 }
}
