package org.folio.circulation.domain.policy.library;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Collections;

public class EndOfCurrentHoursStrategy extends ShortTermLoansBaseStrategy {

  private final DateTime currentTime;

  public EndOfCurrentHoursStrategy(DateTime currentTime, DateTimeZone zone) {
    super(zone);
    this.currentTime = currentTime;
  }

  @Override
  protected HttpResult<DateTime> calculateIfClosed(LibraryTimetable libraryTimetable, LibraryInterval requestedInterval) {
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
