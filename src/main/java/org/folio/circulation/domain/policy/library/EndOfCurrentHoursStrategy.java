package org.folio.circulation.domain.policy.library;

import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;

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
      return Result.failed(failureForAbsentTimetable());
    }
    if (currentTimeInterval.isOpen()) {
      return Result.succeeded(currentTimeInterval.getEndTime());
    }
    return Result.succeeded(currentTimeInterval.getNext().getEndTime());
  }
}
