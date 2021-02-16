
package org.folio.circulation.domain.representations;

import static org.folio.circulation.resources.handlers.error.OverridableBlockType.ITEM_LIMIT_BLOCK;
import static org.folio.circulation.resources.handlers.error.OverridableBlockType.ITEM_NOT_LOANABLE_BLOCK;
import static org.folio.circulation.resources.handlers.error.OverridableBlockType.PATRON_BLOCK;
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
      getObjectProperty(representation, ITEM_NOT_LOANABLE_BLOCK.getName()));
    PatronBlock patronBlock = getObjectProperty(representation, PATRON_BLOCK.getName()) != null
      ? new PatronBlock()
      : null;
    ItemLimitBlock itemLimitBlock = getObjectProperty(
      representation, ITEM_LIMIT_BLOCK.getName()) != null
      ? new ItemLimitBlock()
      : null;
    String comment = getProperty(representation, "comment");

    return new OverrideBlocks(
      itemNotLoanableBlock, patronBlock, itemLimitBlock, comment);
  }

  public boolean isItemLimitBlockOverriding() {
    return this.getItemLimitBlock() != null;
  }

  public boolean isPatronBlockOverriding() {
    return this.getPatronBlock() != null;
  }

  public boolean isItemNotLoanableBlock() {
    return this.getItemNotLoanableBlock() != null;
  }
}
