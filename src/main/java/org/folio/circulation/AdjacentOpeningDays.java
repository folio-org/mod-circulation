package org.folio.circulation;

import org.folio.circulation.domain.OpeningDay;

import java.util.Objects;

public class AdjacentOpeningDays {

  private final OpeningDay previousDay;
  private final OpeningDay requestedDay;
  private final OpeningDay nextDay;

  public AdjacentOpeningDays(OpeningDay previousDay, OpeningDay requestedDay, OpeningDay nextDay) {
    Objects.requireNonNull(previousDay);
    Objects.requireNonNull(requestedDay);
    Objects.requireNonNull(nextDay);
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
