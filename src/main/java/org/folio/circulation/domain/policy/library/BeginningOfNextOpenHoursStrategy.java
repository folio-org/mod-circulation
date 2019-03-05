package org.folio.circulation.domain.policy.library;

import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.HttpResult;
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
  protected HttpResult<DateTime> calculateIfClosed(LibraryTimetable libraryTimetable, LibraryInterval requestedInterval) {
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
