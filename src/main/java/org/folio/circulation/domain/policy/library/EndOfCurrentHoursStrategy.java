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
      log.warn("calculateIfClosed:: currentTimeInterval is null");
      return calculateIfCurrentTimeIsOutsideOfTimetable(requestedInterval);
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

  /**
   * The timetable is built from the days surrounding the projected due date only, so for loans
   * longer than a day (e.g. 48 hours) the checkout time can be earlier than the whole timetable.
   * Everything in the timetable is then later than the checkout time, so the end of the last
   * open hours before the projected due date is the end of the service point hours to move to.
   */
  private Result<ZonedDateTime> calculateIfCurrentTimeIsOutsideOfTimetable(
    LibraryInterval requestedInterval) {

    LibraryInterval previousInterval = requestedInterval.getPrevious();
    if (previousInterval != null && previousInterval.isOpen()
      && previousInterval.getEndTime().isAfter(currentTime)) {

      log.info("calculateIfCurrentTimeIsOutsideOfTimetable:: moving to the end of the last " +
        "open hours before requested date: {}", previousInterval.getEndTime());
      return succeeded(previousInterval.getEndTime());
    }

    log.error("calculateIfCurrentTimeIsOutsideOfTimetable:: no open hours found for " +
      "current time {}", currentTime);
    return failed(failureForAbsentTimetable());
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
