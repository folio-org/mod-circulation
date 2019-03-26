package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.folio.circulation.support.Result.succeeded;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class KeepCurrentDateStrategy implements ClosedLibraryStrategy {

  private final DateTimeZone zone;

  public KeepCurrentDateStrategy(DateTimeZone zone) {
    this.zone = zone;
  }

  @Override
  public Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    return succeeded(requestedDate.withZone(zone).withTime(END_OF_A_DAY));
  }
}
