package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.folio.circulation.support.results.Result.succeeded;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class KeepCurrentDateStrategy implements ClosedLibraryStrategy {

  private final DateTimeZone zone;

  public KeepCurrentDateStrategy(DateTimeZone zone) {
    this.zone = zone;
  }

  @Override
  public Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    final DateTime dueDate = requestedDate.withZone(zone)
      // Always keep the requested date
      .withDate(requestedDate.toLocalDate())
      .withTime(END_OF_A_DAY);

    return succeeded(dueDate);
  }
}
