package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Objects;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;

public abstract class ShortTermLoansBaseStrategy implements ClosedLibraryStrategy {

  private final DateTimeZone zone;

  protected ShortTermLoansBaseStrategy(DateTimeZone zone) {
    this.zone = zone;
  }

  @Override
  public Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    LibraryTimetable libraryTimetable =
      LibraryTimetableConverter.convertToLibraryTimetable(openingDays, zone);

    LibraryInterval requestedInterval = libraryTimetable.findInterval(requestedDate);
    if (requestedInterval == null) {
      return Result.failed(failureForAbsentTimetable());
    }
    if (requestedInterval.isOpen()) {
      return Result.succeeded(requestedDate);
    }
    return calculateIfClosed(libraryTimetable, requestedInterval);
  }

  protected abstract Result<DateTime> calculateIfClosed(
    LibraryTimetable libraryTimetable, LibraryInterval requestedInterval);
}
