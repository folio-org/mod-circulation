package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.results.Result;

public class EndOfCurrentHoursStrategy extends ShortTermLoansBaseStrategy {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ZonedDateTime currentTime;

  public EndOfCurrentHoursStrategy(ZonedDateTime currentTime, ZoneId zone) {
    super(zone);
    this.currentTime = currentTime;
  }

  @Override
  protected Result<ZonedDateTime> calculateIfClosed(LibraryTimetable libraryTimetable,
    LibraryInterval requestedInterval) {

    log.debug("calculateIfClosed:: parameters libraryTimetable: {}, requestedInterval: {}",
      libraryTimetable, requestedInterval);
    LibraryInterval currentTimeInterval = libraryTimetable.findInterval(currentTime);
    if (currentTimeInterval == null) {
      log.error("calculateIfClosed:: currentTimeInterval is null");
      return failed(failureForAbsentTimetable());
    }
    if (hasLibraryRolloverWorkingDay(libraryTimetable, requestedInterval)) {
      log.info("calculateIfClosed:: hasLibraryRolloverWorkingDay is true");
      return succeeded(requestedInterval.getPrevious().getEndTime());
    }
    if (currentTimeInterval.isOpen()) {
      log.info("calculateIfClosed:: current time interval is open");
      return succeeded(currentTimeInterval.getEndTime());
    }
    return succeeded(currentTimeInterval.getNext().getEndTime())
      .next(dateTime -> {
        log.info("calculateIfClosed:: result: {}", dateTime);
        return succeeded(dateTime);
      });
  }

  private boolean hasLibraryRolloverWorkingDay(LibraryTimetable libraryTimetable,
    LibraryInterval requestedInterval) {

    log.debug("hasLibraryRolloverWorkingDay:: parameters libraryTimetable: {}, " +
        "requestedInterval: {}", libraryTimetable, requestedInterval);
    if (isNotSequenceOfWorkingDays(libraryTimetable, requestedInterval)) {
      log.info("hasLibraryRolloverWorkingDay:: isNotSequenceOfWorkingDays is true");
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
