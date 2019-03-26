package org.folio.circulation.domain.policy.library;

import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;

public class BeginningOfNextOpenHoursStrategy extends ShortTermLoansBaseStrategy {

  private final Period offsetPeriod;

  public BeginningOfNextOpenHoursStrategy(
    LoanPolicyPeriod offsetInterval, int offsetDuration, DateTimeZone zone) {
    super(zone);
    offsetPeriod = LoanPolicyPeriod.calculatePeriod(offsetInterval, offsetDuration);
  }

  @Override
  protected Result<DateTime> calculateIfClosed(LibraryTimetable libraryTimetable, LibraryInterval requestedInterval) {
    LibraryInterval nextInterval = requestedInterval.getNext();
    if (nextInterval == null) {
      return Result.failed(failureForAbsentTimetable());
    }
    DateTime dueDateWithOffset = nextInterval.getStartTime().plus(offsetPeriod);
    if (nextInterval.getInterval().contains(dueDateWithOffset)) {
      return Result.succeeded(dueDateWithOffset);
    }

    LibraryInterval intervalForDateWithOffset =
      libraryTimetable.findInterval(dueDateWithOffset);
    if (intervalForDateWithOffset == null) {
      return Result.succeeded(libraryTimetable.getTail().getEndTime());
    }
    if (intervalForDateWithOffset.isOpen()) {
      return Result.succeeded(dueDateWithOffset);
    }
    return Result.succeeded(intervalForDateWithOffset.getPrevious().getEndTime());
  }
}
