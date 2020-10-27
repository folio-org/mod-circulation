package org.folio.circulation.domain;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ItemLostRequiringCostsEntry {
  private final Item item;
  private final Loan loan;

  public ItemLostRequiringCostsEntry(Item item) {
    this.item = item;
    this.loan = null;
  }

  public Item getItem() {
    return item;
  }

  public Loan getLoan() {
    return loan;
  }

  public ItemLostRequiringCostsEntry withItem(Item item) {
    return new ItemLostRequiringCostsEntry(item, this.loan);
  }

  public ItemLostRequiringCostsEntry withLoan(Loan loan) {
    return new ItemLostRequiringCostsEntry(this.item, loan);
  }
}
