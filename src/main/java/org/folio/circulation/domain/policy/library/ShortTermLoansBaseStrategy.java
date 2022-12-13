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

    LibraryInterval requestedInterval = libraryTimetable.findInterval(requestedDate);
    if (requestedInterval == null) {
      return failed(failureForAbsentTimetable());
    }
    if (requestedInterval.isOpen()) {
      return succeeded(requestedDate);
    }
    return calculateIfClosed(libraryTimetable, requestedInterval);
  }

  protected abstract Result<ZonedDateTime> calculateIfClosed(
    LibraryTimetable libraryTimetable, LibraryInterval requestedInterval);
}
