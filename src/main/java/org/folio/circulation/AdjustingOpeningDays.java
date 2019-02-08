package org.folio.circulation;

import org.folio.circulation.domain.OpeningDay;

public class AdjustingOpeningDays {

  private final OpeningDay previousDay;
  private final OpeningDay requestedDay;
  private final OpeningDay nextDay;

  public AdjustingOpeningDays(OpeningDay previousDay, OpeningDay requestedDay, OpeningDay nextDay) {
    this.previousDay = previousDay;
    this.requestedDay = requestedDay;
    this.nextDay = nextDay;
  }

  public OpeningDay getPreviousDay() {
    return previousDay;
  }

  public OpeningDay getRequestedDay() {
    return requestedDay;
  }

  public OpeningDay getNextDay() {
    return nextDay;
  }
}
