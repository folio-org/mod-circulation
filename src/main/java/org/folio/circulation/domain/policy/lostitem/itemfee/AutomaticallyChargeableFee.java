package org.folio.circulation.domain.policy.lostitem.itemfee;

import java.math.BigDecimal;

public final class AutomaticallyChargeableFee implements ChargeableFee {
  private final BigDecimal amount;

  public AutomaticallyChargeableFee(BigDecimal amount) {
    this.amount = amount;
  }

  public final BigDecimal getAmount() {
    return amount;
  }

  @Override
  public final boolean isChargeable() {
    return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
  }

  public static AutomaticallyChargeableFee noAutomaticallyChargeableFee() {
    return new AutomaticallyChargeableFee(null);
  }
}
