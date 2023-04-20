
package org.folio.circulation.domain.override;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class BlockOverrides {
  private final ItemNotLoanableBlockOverride itemNotLoanableBlockOverride;
  private final PatronBlockOverride patronBlockOverride;
  private final ItemLimitBlockOverride itemLimitBlockOverride;
  private final RenewalBlockOverride renewalBlockOverride;
  private final RenewalDueDateRequiredBlockOverride renewalDueDateRequiredBlockOverride;
  private final String comment;
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static BlockOverrides from(JsonObject representation) {
    log.debug("from:: parameters representation: {}", representation);

    return new BlockOverrides(
      ItemNotLoanableBlockOverride.from(getObjectProperty(representation, "itemNotLoanableBlock")),
      PatronBlockOverride.from(getObjectProperty(representation, "patronBlock")),
      ItemLimitBlockOverride.from(getObjectProperty(representation, "itemLimitBlock")),
      RenewalBlockOverride.from(getObjectProperty(representation, "renewalBlock")),
      RenewalDueDateRequiredBlockOverride.from(getObjectProperty(representation,
        "renewalDueDateRequiredBlock")),
      getProperty(representation, "comment"));
  }

  public static BlockOverrides fromRequest(JsonObject requestRepresentation) {
    log.debug("fromRequest:: parameters requestRepresentation: {}", requestRepresentation);

    return from(getNestedObjectProperty(
      requestRepresentation, "requestProcessingParameters", "overrideBlocks"));
  }

  public static BlockOverrides noOverrides() {
    log.debug("noOverrides:: ");

    return from(null);
  }
}
