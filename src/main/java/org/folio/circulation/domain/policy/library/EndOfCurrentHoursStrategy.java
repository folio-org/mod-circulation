package org.folio.circulation.domain.policy.library;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Collections;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;

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
      return HttpResult.failed(failureForAbsentTimetable());
    }
    if (!currentTimeInterval.isOpen()) {
      return HttpResult.failed(errorForClosedCurrentInterval());
    }
    return HttpResult.succeeded(currentTimeInterval.getEndTime());
  }

  private ValidationErrorFailure errorForClosedCurrentInterval() {
    String message =
      "Unable to use end of current service point hours as service point is defined to be closed now";
    return new ValidationErrorFailure(new ValidationError(message, Collections.emptyMap()));
  }
}
