package org.folio.circulation.domain.override;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;

import java.time.ZonedDateTime;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class RenewalDueDateRequiredBlockOverride extends BlockOverride {
  private static final String DUE_DATE_FIELD_NAME = "dueDate";

  private final ZonedDateTime dueDate;

  private RenewalDueDateRequiredBlockOverride(boolean requested, ZonedDateTime dueDate) {
    super(requested);
    this.dueDate = dueDate;
  }

  public static RenewalDueDateRequiredBlockOverride from(JsonObject representation) {
    return new RenewalDueDateRequiredBlockOverride(
      representation != null,
      getDateTimeProperty(representation, DUE_DATE_FIELD_NAME));
  }
}
