package org.folio.circulation.domain.override;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class RenewalDueDateRequiredBlockOverride extends BlockOverride {
  private static final String DUE_DATE_FIELD_NAME = "dueDate";
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ZonedDateTime dueDate;

  private RenewalDueDateRequiredBlockOverride(boolean requested, ZonedDateTime dueDate) {
    super(requested);
    this.dueDate = dueDate;
  }

  public static RenewalDueDateRequiredBlockOverride from(JsonObject representation) {
    log.debug("from:: parameters representation: {}", representation);

    return new RenewalDueDateRequiredBlockOverride(
      representation != null,
      getDateTimeProperty(representation, DUE_DATE_FIELD_NAME));
  }
}
