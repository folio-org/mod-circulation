package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@AllArgsConstructor
@Getter
@With
public class ItemLostRequiringCostsEntry {
  private final Item item;
  private final Loan loan;

  public ItemLostRequiringCostsEntry(Item item) {
    this.item = item;
    this.loan = null;
  }
}
