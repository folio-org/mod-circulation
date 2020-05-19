package org.folio.circulation.domain.policy.lostitem.itemfee;

import static org.folio.circulation.domain.FeeAmount.noFeeAmount;

import java.math.BigDecimal;

import org.folio.circulation.domain.FeeAmount;

public final class AutomaticallyChargeableFee implements ChargeableFee {
  private final FeeAmount amount;

  public AutomaticallyChargeableFee(BigDecimal amount) {
    this(new FeeAmount(amount));
  }

  private AutomaticallyChargeableFee(FeeAmount amount) {
    this.amount = amount;
  }

  public final FeeAmount getAmount() {
    return amount;
  }

  @Override
  public final boolean isChargeable() {
    return amount.hasAmount();
  }

  public static AutomaticallyChargeableFee noAutomaticallyChargeableFee() {
    return new AutomaticallyChargeableFee(noFeeAmount());
  }
}
