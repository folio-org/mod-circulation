package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class KeepCurrentDateStrategy implements ClosedLibraryStrategy {

  private final DateTimeZone zone;

  public KeepCurrentDateStrategy(DateTimeZone zone) {
    this.zone = zone;
  }

  @Override
  public HttpResult<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    return HttpResult.succeeded(
      requestedDate.withZone(zone).withTime(END_OF_A_DAY));
  }
}
