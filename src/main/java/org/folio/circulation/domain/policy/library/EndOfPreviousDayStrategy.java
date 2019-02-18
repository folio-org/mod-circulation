package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.support.HttpResult;
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
  public HttpResult<DateTime> calculateDueDate(DateTime requestedDate, AdjustingOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    if (openingDays.getRequestedDay().getOpen()) {
      return HttpResult.succeeded(
        requestedDate.withZone(zone).withTime(END_OF_A_DAY));
    }
    OpeningDay previousDay = openingDays.getPreviousDay();
    if (!previousDay.getOpen()) {
      return HttpResult.failed(failureForAbsentTimetable());
    }
    return HttpResult.succeeded(
      previousDay.getDate().toDateTime(END_OF_A_DAY, zone));
  }
}
