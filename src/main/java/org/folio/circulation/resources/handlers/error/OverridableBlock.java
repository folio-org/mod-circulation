package org.folio.circulation.resources.handlers.error;

public enum OverridableBlock {
  ITEM_NOT_LOANABLE("item-not-loanable"),
  ITEM_LIMIT("item-limit"),
  PATRON_BLOCK("patron-block");

  String blockName;

  OverridableBlock(String blockName) {
    this.blockName = blockName;
  }

  public String getBlockName() {
    return blockName;
  }
}
