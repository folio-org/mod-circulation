package org.folio.circulation.domain.policy;

public class OverdueFinePolicyFineInfo {
  private final Double overdueFine;
  private final OverdueFineInterval overdueFineInterval;
  private final Double overdueRecallFine;
  private final OverdueFineInterval overdueRecallFineInterval;

  public OverdueFinePolicyFineInfo(Double overdueFine, OverdueFineInterval overdueFineInterval,
    Double overdueRecallFine, OverdueFineInterval overdueRecallFineInterval) {
    this.overdueFine = overdueFine;
    this.overdueFineInterval = overdueFineInterval;
    this.overdueRecallFine = overdueRecallFine;
    this.overdueRecallFineInterval = overdueRecallFineInterval;
  }

  public Double getOverdueFine() {
    return overdueFine;
  }

  public OverdueFineInterval getOverdueFineInterval() {
    return overdueFineInterval;
  }

  public Double getOverdueRecallFine() {
    return overdueRecallFine;
  }

  public OverdueFineInterval getOverdueRecallFineInterval() {
    return overdueRecallFineInterval;
  }
}
