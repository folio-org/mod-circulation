
package org.folio.circulation.domain.override;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

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

  public static BlockOverrides from(JsonObject representation) {
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
    return from(getNestedObjectProperty(
      requestRepresentation, "requestProcessingParameters", "overrideBlocks"));
  }

  public static BlockOverrides noOverrides() {
    return from(null);
  }
}