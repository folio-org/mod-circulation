package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;

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
    return succeeded(atEndOfDay(requestedDate, zone));
  }
}
