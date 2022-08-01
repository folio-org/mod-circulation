package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.support.results.Result;

public class EndOfPreviousDayStrategy implements ClosedLibraryStrategy {

  protected final ZoneId zone;

  public EndOfPreviousDayStrategy(ZoneId zone) {
    this.zone = zone;
  }

  @Override
  public Result<ZonedDateTime> calculateDueDate(ZonedDateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    if (openingDays.getRequestedDay().isOpen()) {
      return succeeded(atEndOfDay(requestedDate, zone));
    }
    OpeningDay previousDay = openingDays.getPreviousDay();
    if (!previousDay.isOpen()) {
      return failed(failureForAbsentTimetable());
    }

    return succeeded(atEndOfDay(previousDay.getDate(), zone));
  }
}
