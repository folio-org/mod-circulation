package org.folio.circulation.domain.policy;

import java.math.BigDecimal;

public class OverdueFinePolicyLimitInfo {
  private final BigDecimal maxOverdueFine;
  private final BigDecimal maxOverdueRecallFine;

  public OverdueFinePolicyLimitInfo(BigDecimal maxOverdueFine, BigDecimal maxOverdueRecallFine) {
    this.maxOverdueFine = maxOverdueFine;
    this.maxOverdueRecallFine = maxOverdueRecallFine;
  }

  public BigDecimal getMaxOverdueFine() {
    return maxOverdueFine;
  }

  public BigDecimal getMaxOverdueRecallFine() {
    return maxOverdueRecallFine;
  }
}
