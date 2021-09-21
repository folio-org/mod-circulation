package org.folio.circulation.domain.policy;

import java.math.BigDecimal;

public class OverdueFineCalculationParameters {
  private final BigDecimal finePerInterval;
  private final OverdueFineInterval interval;
  private final BigDecimal maxFine;

  public OverdueFineCalculationParameters(BigDecimal finePerInterval, OverdueFineInterval interval,
    BigDecimal maxFine) {
    this.finePerInterval = finePerInterval;
    this.interval = interval;
    this.maxFine = maxFine;
  }

  public BigDecimal getFinePerInterval() {
    return finePerInterval;
  }

  public OverdueFineInterval getInterval() {
    return interval;
  }

  public BigDecimal getMaxFine() {
    return maxFine;
  }
}
