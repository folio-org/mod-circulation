package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

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
      return failed(failureForAbsentTimetable());
    }
    DateTime dueDateWithOffset = nextInterval.getStartTime().plus(offsetPeriod);
    if (nextInterval.getInterval().contains(dueDateWithOffset)) {
      return succeeded(dueDateWithOffset);
    }

    LibraryInterval intervalForDateWithOffset =
      libraryTimetable.findInterval(dueDateWithOffset);
    if (intervalForDateWithOffset == null) {
      return succeeded(libraryTimetable.getTail().getEndTime());
    }
    if (intervalForDateWithOffset.isOpen()) {
      return succeeded(dueDateWithOffset);
    }
    return succeeded(intervalForDateWithOffset.getPrevious().getEndTime());
  }
}
