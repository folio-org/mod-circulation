package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.folio.circulation.support.results.Result;

public class EndOfCurrentHoursStrategy extends ShortTermLoansBaseStrategy {

  private final ZonedDateTime currentTime;

  public EndOfCurrentHoursStrategy(ZonedDateTime currentTime, ZoneId zone) {
    super(zone);
    this.currentTime = currentTime;
  }

  @Override
  protected Result<ZonedDateTime> calculateIfClosed(LibraryTimetable libraryTimetable, LibraryInterval requestedInterval) {
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

  private boolean hasLibraryRolloverWorkingDay(LibraryTimetable libraryTimetable, LibraryInterval requestedInterval) {
    if (isNotSequenceOfWorkingDays(libraryTimetable, requestedInterval)) {
      return false;
    }

    ZonedDateTime endLocalTime = libraryTimetable.getHead().getEndTime();
    ZonedDateTime startLocalTime = requestedInterval.getPrevious().getStartTime();

    return isDateEqualToBoundaryValueOfDay(endLocalTime, atEndOfDay(endLocalTime))
      && isDateEqualToBoundaryValueOfDay(startLocalTime, atStartOfDay(startLocalTime));
  }

  private boolean isNotSequenceOfWorkingDays(LibraryTimetable libraryTimetable, LibraryInterval requestedInterval) {
    // Two consecutive days are 1-day apart (~24 hours are between their respective start hours).
    // Non-consecutive days are therefore greater than 1 day apart.
    return daysBetween(libraryTimetable.getHead().getEndTime(),
      requestedInterval.getPrevious().getStartTime()) > 1;
  }

  private boolean isDateEqualToBoundaryValueOfDay(ZonedDateTime requestedInterval, ZonedDateTime boundaryValueOfDay) {
    final LocalTime request = requestedInterval.truncatedTo(ChronoUnit.MINUTES).toLocalTime();
    final LocalTime boundary = boundaryValueOfDay.truncatedTo(ChronoUnit.MINUTES).toLocalTime();

    return request.compareTo(boundary) == 0;
  }

  private long daysBetween(ZonedDateTime begin, ZonedDateTime end) {
    return Math.abs(Duration.between(atStartOfDay(begin), atStartOfDay(end)).toDays());
  }

}
