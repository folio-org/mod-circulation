package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Objects;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class EndOfPreviousOpenHoursTruncateStrategy extends ShortTermLoansBaseStrategy {

  private final DateTime startDateTime;

  public EndOfPreviousOpenHoursTruncateStrategy(DateTime startDateTime, DateTimeZone zone) {
    super(zone);
    this.startDateTime = startDateTime;
  }

  @Override
  public Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    LibraryTimetable libraryTimetable =
      LibraryTimetableConverter.convertToLibraryTimetable(openingDays, zone);

    LibraryInterval requestedInterval = libraryTimetable.findInterval(requestedDate);
    if (requestedInterval == null) {
      return failed(failureForAbsentTimetable());
    }

    return calculateIfClosed(libraryTimetable, requestedInterval);
  }

  @Override
  protected Result<DateTime> calculateIfClosed(LibraryTimetable libraryTimetable,
    LibraryInterval requestedInterval) {

    LibraryInterval currentTimeInterval = libraryTimetable.findInterval(startDateTime);
    if (currentTimeInterval == null) {
      return failed(failureForAbsentTimetable());
    }

    return succeeded(currentTimeInterval.getPrevious().getEndTime());
  }
}
