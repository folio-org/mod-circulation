package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class EndOfPreviousHoursStrategy extends ShortTermLoansBaseStrategy {

  private final DateTime currentTime;

  public EndOfPreviousHoursStrategy(DateTime currentTime, DateTimeZone zone) {
    super(zone);
    this.currentTime = currentTime;
  }

  @Override
  protected Result<DateTime> calculateIfClosed(LibraryTimetable libraryTimetable,
    LibraryInterval requestedInterval) {

    LibraryInterval currentTimeInterval = libraryTimetable.findInterval(currentTime);
    if (currentTimeInterval == null) {
      return failed(failureForAbsentTimetable());
    }

    return succeeded(currentTimeInterval.getPrevious().getEndTime());
  }
}
