package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Collections;
import java.util.Objects;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;

public class EndOfCurrentHoursStrategy implements ClosedLibraryStrategy {

  private final DateTime currentTime;
  private final DateTimeZone zone;

  public EndOfCurrentHoursStrategy(
    DateTime currentTime, DateTimeZone zone) {
    this.currentTime = currentTime;
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

    LibraryInterval currentTimeInterval = libraryTimetable.findInterval(currentTime);
    if (currentTimeInterval == null) {
      return HttpResult.failed(errorForAbsentCurrentInterval());
    }
    if (!currentTimeInterval.isOpen()) {
      return HttpResult.failed(errorForClosedCurrentInterval());
    }
    return HttpResult.succeeded(currentTimeInterval.getEndTime());
  }

  private ValidationErrorFailure errorForAbsentCurrentInterval() {
    String message = "Unable to find current service point hours";
    return new ValidationErrorFailure(new ValidationError(message, Collections.emptyMap()));
  }

  private ValidationErrorFailure errorForClosedCurrentInterval() {
    String message = "Current service point hours are closed";
    return new ValidationErrorFailure(new ValidationError(message, Collections.emptyMap()));
  }


}
