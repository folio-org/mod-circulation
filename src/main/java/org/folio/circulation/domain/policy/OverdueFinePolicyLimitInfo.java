package org.folio.circulation.domain.policy;

public class OverdueFinePolicyLimitInfo {
  private final Double maxOverdueFine;
  private final Double maxOverdueRecallFine;

  public OverdueFinePolicyLimitInfo(Double maxOverdueFine, Double maxOverdueRecallFine) {
    this.maxOverdueFine = maxOverdueFine;
    this.maxOverdueRecallFine = maxOverdueRecallFine;
  }

  public Double getMaxOverdueFine() {
    return maxOverdueFine;
  }

  public Double getMaxOverdueRecallFine() {
    return maxOverdueRecallFine;
  }
}
