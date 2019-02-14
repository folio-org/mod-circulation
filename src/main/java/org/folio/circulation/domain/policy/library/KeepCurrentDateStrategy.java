package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;

public class KeepCurrentDateStrategy implements ClosedLibraryStrategy {

  private final DateTimeZone zone;

  public KeepCurrentDateStrategy(DateTimeZone zone) {
    this.zone = zone;
  }

  @Override
  public DateTime calculateDueDate(DateTime requestedDate, AdjustingOpeningDays openingDays) {
    return requestedDate.withZone(zone).withTime(END_OF_A_DAY);
  }
}
