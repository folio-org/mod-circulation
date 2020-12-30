
package org.folio.circulation.domain.representations;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class ItemLimitBlock {

  public static ItemLimitBlock from(JsonObject representation) {
    if (representation != null) {
      return new ItemLimitBlock();
    }
    return null;
  }
}
