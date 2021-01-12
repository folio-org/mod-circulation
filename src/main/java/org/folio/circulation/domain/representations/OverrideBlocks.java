
package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OverrideBlocks {

  private final ItemNotLoanableBlock itemNotLoanableBlock;
  private final PatronBlock patronBlock;
  private final ItemLimitBlock itemLimitBlock;

  public static OverrideBlocks from(JsonObject representation) {
    if (representation != null) {
      ItemNotLoanableBlock itemNotLoanableBlock = ItemNotLoanableBlock.from(
        getObjectProperty(representation, "itemNotLoanableBlock"));
      PatronBlock patronBlock = PatronBlock.from(
        getObjectProperty(representation, "patronBlock"));
      ItemLimitBlock itemLimitBlock = ItemLimitBlock.from(
        getObjectProperty(representation, "itemLimitBlock"));

      return new OverrideBlocks(
        itemNotLoanableBlock, patronBlock, itemLimitBlock);
    }
    return null;
  }
}
