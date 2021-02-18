
package org.folio.circulation.domain.override;

import io.vertx.core.json.JsonObject;

public class ItemLimitBlockOverride extends BlockOverride {

  public ItemLimitBlockOverride(boolean requested) {
    super(requested);
  }

  public static ItemLimitBlockOverride from(JsonObject representation) {
    return new ItemLimitBlockOverride(representation != null);
  }
}
