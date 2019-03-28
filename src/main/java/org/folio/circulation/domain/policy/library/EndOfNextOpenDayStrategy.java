package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Objects;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class EndOfNextOpenDayStrategy implements ClosedLibraryStrategy {

  private final DateTimeZone zone;

  public EndOfNextOpenDayStrategy(DateTimeZone zone) {
    this.zone = zone;
  }

  @Override
  public Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    if (openingDays.getRequestedDay().getOpen()) {
      return succeeded(
        requestedDate.withZone(zone).withTime(END_OF_A_DAY));
    }
    OpeningDay nextDay = openingDays.getNextDay();
    if (!nextDay.getOpen()) {
      return failed(failureForAbsentTimetable());
    }
    return succeeded(nextDay.getDate().toDateTime(END_OF_A_DAY, zone));
  }
}
