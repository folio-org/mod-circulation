package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

import java.util.Objects;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;

public class BeginningOfNextOpenHoursStrategy implements ClosedLibraryStrategy {

  private final Period offsetPeriod;
  private final DateTimeZone zone;

  public BeginningOfNextOpenHoursStrategy(
    LoanPolicyPeriod offsetInterval,
    int offsetDuration,
    DateTimeZone zone) {
    offsetPeriod = LoanPolicyPeriod.calculatePeriod(offsetInterval, offsetDuration);
    this.zone = zone;
  }

  @Override
  public HttpResult<DateTime> calculateDueDate(DateTime requestedDate, AdjustingOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    LibraryTimetable libraryTimetable =
      LibraryTimetableConverter.convertToLibraryTimetable(openingDays, zone);

    LibraryInterval requestedInterval =
      libraryTimetable.findInterval(requestedDate);
    if (requestedInterval == null) {
      return HttpResult.failed(failureForAbsentTimetable());
    }
    if (requestedInterval.isOpen()) {
      return HttpResult.succeeded(requestedDate);
    }

    LibraryInterval nextInterval = requestedInterval.getNext();
    if (nextInterval == null) {
      return HttpResult.failed(failureForAbsentTimetable());
    }
    DateTime dueDateWithOffset = nextInterval.getStartTime().plus(offsetPeriod);
    if (nextInterval.getInterval().contains(dueDateWithOffset)) {
      return HttpResult.succeeded(dueDateWithOffset);
    }

    LibraryInterval intervalForDateWithOffset =
      libraryTimetable.findInterval(dueDateWithOffset);
    if (intervalForDateWithOffset == null) {
      return HttpResult.succeeded(libraryTimetable.getTail().getEndTime());
    }
    if (intervalForDateWithOffset.isOpen()) {
      return HttpResult.succeeded(dueDateWithOffset);
    }
    return HttpResult.succeeded(intervalForDateWithOffset.getPrevious().getEndTime());
  }
}
