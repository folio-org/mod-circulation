package org.folio.circulation.domain.policy.lostitem.itemfee;

public final class ActualCostFee implements ChargeableFee {
  @Override
  public final boolean isChargeable() {
    return true;
  }

  public static ChargeableFee noActualCostFee() {
    return () -> false;
  }
}
