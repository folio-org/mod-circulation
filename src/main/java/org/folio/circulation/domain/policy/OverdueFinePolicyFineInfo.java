package org.folio.circulation.domain.policy;

import java.math.BigDecimal;

public class OverdueFinePolicyFineInfo {
  private final BigDecimal overdueFine;
  private final OverdueFineInterval overdueFineInterval;
  private final BigDecimal overdueRecallFine;
  private final OverdueFineInterval overdueRecallFineInterval;

  public OverdueFinePolicyFineInfo(BigDecimal overdueFine, OverdueFineInterval overdueFineInterval,
    BigDecimal overdueRecallFine, OverdueFineInterval overdueRecallFineInterval) {
    this.overdueFine = overdueFine;
    this.overdueFineInterval = overdueFineInterval;
    this.overdueRecallFine = overdueRecallFine;
    this.overdueRecallFineInterval = overdueRecallFineInterval;
  }

  public BigDecimal getOverdueFine() {
    return overdueFine;
  }

  public OverdueFineInterval getOverdueFineInterval() {
    return overdueFineInterval;
  }

  public BigDecimal getOverdueRecallFine() {
    return overdueRecallFine;
  }

  public OverdueFineInterval getOverdueRecallFineInterval() {
    return overdueRecallFineInterval;
  }
}
