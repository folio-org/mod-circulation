package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.support.HttpResult;
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
  public HttpResult<DateTime> calculateDueDate(DateTime requestedDate, AdjustingOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    LibraryTimetable libraryTimetable =
      LibraryTimetableConverter.convertToLibraryTimetable(openingDays, zone);

    LibraryInterval requestedInterval = libraryTimetable.findInterval(requestedDate);
    if (requestedInterval == null) {
      return HttpResult.failed(failureForAbsentTimetable());
    }
    if (requestedInterval.isOpen()) {
      return HttpResult.succeeded(requestedDate);
    }
    return calculateIfClosed(libraryTimetable, requestedInterval);
  }

  protected abstract HttpResult<DateTime> calculateIfClosed(
    LibraryTimetable libraryTimetable, LibraryInterval requestedInterval);
}
