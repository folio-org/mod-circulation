package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.support.results.Result.succeeded;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;

public class KeepCurrentDateStrategy implements ClosedLibraryStrategy {
  private final DateTimeZone zone;

  public KeepCurrentDateStrategy(DateTimeZone zone) {
    this.zone = zone;
  }

  @Override
  public Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    // TODO: this introduces behavioral change not yet intended, use this after converting JodaTime to JavaTime.
    //return succeeded(atEndOfDay(requestedDate, zone).withZone(UTC));
    return succeeded(requestedDate
      .withZoneRetainFields(zone)
      .withTime(LocalTime.MIDNIGHT.minusSeconds(1)));
  }
}
