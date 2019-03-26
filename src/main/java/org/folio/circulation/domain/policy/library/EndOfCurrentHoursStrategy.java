package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class EndOfCurrentHoursStrategy extends ShortTermLoansBaseStrategy {

  private final DateTime currentTime;

  public EndOfCurrentHoursStrategy(DateTime currentTime, DateTimeZone zone) {
    super(zone);
    this.currentTime = currentTime;
  }

  @Override
  protected Result<DateTime> calculateIfClosed(LibraryTimetable libraryTimetable, LibraryInterval requestedInterval) {
    LibraryInterval currentTimeInterval = libraryTimetable.findInterval(currentTime);
    if (currentTimeInterval == null) {
      return failed(failureForAbsentTimetable());
    }
    if (currentTimeInterval.isOpen()) {
      return succeeded(currentTimeInterval.getEndTime());
    }
    return succeeded(currentTimeInterval.getNext().getEndTime());
  }
}
