package org.folio.circulation.domain.policy.lostitem;

import java.math.BigDecimal;

public class ItemFee {
  private final BigDecimal amount;

  protected ItemFee(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public boolean isChargeable() {
    return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
  }
}
