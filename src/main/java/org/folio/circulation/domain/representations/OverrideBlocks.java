
package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OverrideBlocks {

  private final ItemNotLoanableBlock itemNotLoanableBlock;
  private final PatronBlock patronBlock;
  private final ItemLimitBlock itemLimitBlock;
  private final String comment;

  public static OverrideBlocks from(JsonObject representation) {
    if (representation == null) {
      return new OverrideBlocks(null, null, null, null);
    }

    ItemNotLoanableBlock itemNotLoanableBlock = ItemNotLoanableBlock.from(
      getObjectProperty(representation, "itemNotLoanableBlock"));
    PatronBlock patronBlock = getObjectProperty(representation, "patronBlock") != null
      ? new PatronBlock()
      : null;
    ItemLimitBlock itemLimitBlock = getObjectProperty(representation, "itemLimitBlock") != null
      ? new ItemLimitBlock()
      : null;
    String comment = getProperty(representation, "comment");

    return new OverrideBlocks(
      itemNotLoanableBlock, patronBlock, itemLimitBlock, comment);

  }
}
