package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.folio.circulation.domain.TimePeriod;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.results.Result;

public class BeginningOfNextOpenHoursStrategy extends ShortTermLoansBaseStrategy {

  private final Duration duration;

  public BeginningOfNextOpenHoursStrategy(
    LoanPolicyPeriod offsetInterval, int offsetDuration, ZoneId zone) {
    super(zone);
    duration = LoanPolicyPeriod.calculateDuration(offsetInterval, offsetDuration);
  }

  public BeginningOfNextOpenHoursStrategy(Duration intervalDuration, ZoneId zone) {
    super(zone);
    duration = intervalDuration;
  }

  @Override
  protected Result<ZonedDateTime> calculateIfClosed(LibraryTimetable libraryTimetable, LibraryInterval requestedInterval) {
    LibraryInterval nextInterval = requestedInterval.getNext();
    if (nextInterval == null) {
      return failed(failureForAbsentTimetable());
    }

    ZonedDateTime dueDateWithOffset = ZonedDateTime.ofInstant(nextInterval
      .getStartTime().toInstant()
      .plusMillis(duration.toMillis()), zone);

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
