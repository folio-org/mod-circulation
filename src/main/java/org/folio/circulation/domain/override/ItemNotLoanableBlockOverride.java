
package org.folio.circulation.domain.override;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class ItemNotLoanableBlockOverride extends BlockOverride {
  private final DateTime dueDate;

  public ItemNotLoanableBlockOverride(boolean requested, DateTime dueDate) {
    super(requested);
    this.dueDate = dueDate;
  }

  public static ItemNotLoanableBlockOverride from(JsonObject representation) {
    return new ItemNotLoanableBlockOverride(representation != null,
      getDateTimeProperty(representation, "dueDate"));
  }
}

