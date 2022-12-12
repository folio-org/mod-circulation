package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.results.Result;

public abstract class ShortTermLoansBaseStrategy implements ClosedLibraryStrategy {

  protected final ZoneId zone;

  protected ShortTermLoansBaseStrategy(ZoneId zone) {
    this.zone = zone;
  }

  @Override
  public Result<ZonedDateTime> calculateDueDate(ZonedDateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    LibraryTimetable libraryTimetable =
      LibraryTimetableConverter.convertToLibraryTimetable(openingDays, zone);
    System.out.println("TimeTable is " + libraryTimetable.getHead().getInterval().toString());
    System.out.println("Requested date is " +  requestedDate.toString());

    LibraryInterval requestedInterval = libraryTimetable.findInterval(requestedDate);
    if (requestedInterval == null) {
      System.out.println("NUll if ");
      return failed(failureForAbsentTimetable());
    }
    if (requestedInterval.isOpen()) {
      System.out.println("requestedInterval is open " + requestedInterval.getInterval().toString());
      return succeeded(requestedDate);
    }

    System.out.println("Interval is closed "+ requestedInterval.getInterval().toString());
    return calculateIfClosed(libraryTimetable, requestedInterval);
  }

  protected abstract Result<ZonedDateTime> calculateIfClosed(
    LibraryTimetable libraryTimetable, LibraryInterval requestedInterval);
}
