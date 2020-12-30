
package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ItemNotLoanableBlock {

  private DateTime dueDate;

  public static ItemNotLoanableBlock from(JsonObject representation) {

    if (representation != null) {
      return new ItemNotLoanableBlock(
        getDateTimeProperty(representation, "dueDate"));
    }
    return null;
  }
}
