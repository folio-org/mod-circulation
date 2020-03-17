package org.folio.circulation.domain.policy;

public class OverdueFineCalculationParameters {
  private final Double finePerInterval;
  private final OverdueFineInterval interval;
  private final Double maxFine;

  public OverdueFineCalculationParameters(Double finePerInterval, OverdueFineInterval interval,
    Double maxFine) {
    this.finePerInterval = finePerInterval;
    this.interval = interval;
    this.maxFine = maxFine;
  }

  public Double getFinePerInterval() {
    return finePerInterval;
  }

  public OverdueFineInterval getInterval() {
    return interval;
  }

  public Double getMaxFine() {
    return maxFine;
  }
}
