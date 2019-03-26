package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Objects;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;

public class EndOfPreviousDayStrategy implements ClosedLibraryStrategy {

  private final DateTimeZone zone;

  public EndOfPreviousDayStrategy(DateTimeZone zone) {
    this.zone = zone;
  }

  @Override
  public Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    if (openingDays.getRequestedDay().getOpen()) {
      return Result.succeeded(
        requestedDate.withZone(zone).withTime(END_OF_A_DAY));
    }
    OpeningDay previousDay = openingDays.getPreviousDay();
    if (!previousDay.getOpen()) {
      return Result.failed(failureForAbsentTimetable());
    }
    return Result.succeeded(
      previousDay.getDate().toDateTime(END_OF_A_DAY, zone));
  }
}
