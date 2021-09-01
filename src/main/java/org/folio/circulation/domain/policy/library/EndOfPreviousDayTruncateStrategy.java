package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;

import java.util.Objects;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class EndOfPreviousDayTruncateStrategy extends EndOfPreviousDayStrategy {

  public EndOfPreviousDayTruncateStrategy(DateTimeZone zone) {
    super(zone);
  }

  @Override
  public Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    OpeningDay previousDay = openingDays.getPreviousDay();
    if (!previousDay.getOpen()) {
      return failed(failureForAbsentTimetable());
    }

    return succeeded(atEndOfDay(openingDays.getPreviousDay().getDate(), zone));
  }
}
