
package org.folio.circulation.domain.override;

import io.vertx.core.json.JsonObject;

public class PatronBlockOverride extends BlockOverride {

  public PatronBlockOverride(boolean requested) {
    super(requested);
  }

  public static PatronBlockOverride from(JsonObject representation) {
    return new PatronBlockOverride(representation != null);
  }
}
