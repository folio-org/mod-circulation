package org.folio.circulation.domain.policy.lostitem;

import java.math.BigDecimal;

public abstract class ChargeFee {
  private final BigDecimal amount;

  protected ChargeFee(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getAmount() {
    return amount;
  }
}
