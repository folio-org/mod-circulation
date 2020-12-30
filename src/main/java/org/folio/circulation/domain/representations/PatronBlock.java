
package org.folio.circulation.domain.representations;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class PatronBlock {

  public static PatronBlock from(JsonObject representation) {
    if (representation != null) {
      return new PatronBlock();
    }
    return null;
  }
}
