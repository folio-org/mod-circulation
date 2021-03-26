
package org.folio.circulation.domain.override;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class ItemNotLoanableBlockOverride extends BlockOverride {
  private static final String DUE_DATE_FIELD_NAME = "dueDate";

  private final DateTime dueDate;
  private final String dueDateRaw;

  private ItemNotLoanableBlockOverride(boolean requested, DateTime dueDate, String dueDateRaw) {
    super(requested);
    this.dueDate = dueDate;
    this.dueDateRaw = dueDateRaw;
  }

  public static ItemNotLoanableBlockOverride from(JsonObject representation) {
    return new ItemNotLoanableBlockOverride(representation != null,
      getDateTimeProperty(representation, DUE_DATE_FIELD_NAME),
      getProperty(representation, DUE_DATE_FIELD_NAME));
  }
}
