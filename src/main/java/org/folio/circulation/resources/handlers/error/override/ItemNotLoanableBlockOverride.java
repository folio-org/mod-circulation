package org.folio.circulation.resources.handlers.error.override;

import org.joda.time.DateTime;

import lombok.Getter;

@Getter
public class ItemNotLoanableBlockOverride extends BlockOverride {
  private final DateTime dueDate;

  public ItemNotLoanableBlockOverride(DateTime dueDate) {
    super(OverridableBlockType.ITEM_NOT_LOANABLE_BLOCK);
    this.dueDate = dueDate;
  }
}
