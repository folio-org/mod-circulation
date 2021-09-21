package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.compareToMillis;

import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.LocalTime;

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
    if (hasLibraryRolloverWorkingDay(libraryTimetable, requestedInterval)){
      return succeeded(requestedInterval.getPrevious().getEndTime());
    }
    if (currentTimeInterval.isOpen()) {
      return succeeded(currentTimeInterval.getEndTime());
    }
    return succeeded(currentTimeInterval.getNext().getEndTime());
  }

  private boolean hasLibraryRolloverWorkingDay(LibraryTimetable libraryTimetable,
                                               LibraryInterval requestedInterval) {

    if (isNotSequenceOfWorkingDays(libraryTimetable, requestedInterval)) {
      return false;
    }

    LocalTime endLocalTime = libraryTimetable.getHead().getEndTime().toLocalTime();
    LocalTime startLocalTime = requestedInterval.getPrevious().getStartTime().toLocalTime();

    return isDateEqualToBoundaryValueOfDay(endLocalTime, LocalTime.MIDNIGHT.minusMinutes(1))
      && isDateEqualToBoundaryValueOfDay(startLocalTime, LocalTime.MIDNIGHT);
  }

  private boolean isNotSequenceOfWorkingDays(LibraryTimetable libraryTimetable, LibraryInterval requestedInterval) {
    return Days.daysBetween(libraryTimetable.getHead().getEndTime(),
      requestedInterval.getPrevious().getStartTime()) != Days.ZERO;
  }

  private boolean isDateEqualToBoundaryValueOfDay(LocalTime requestedInterval, LocalTime boundaryValueOfDay) {
    return compareToMillis(requestedInterval, boundaryValueOfDay) == 0;
  }
}
