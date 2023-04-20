package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.results.Result;

public class BeginningOfNextOpenHoursStrategy extends ShortTermLoansBaseStrategy {

  private final Duration duration;
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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
  protected Result<ZonedDateTime> calculateIfClosed(LibraryTimetable libraryTimetable,
    LibraryInterval requestedInterval) {

    log.debug("calculateIfClosed:: parameters libraryTimetable: {}, requestedInterval: {}",
      libraryTimetable, requestedInterval);
    LibraryInterval nextInterval = requestedInterval.getNext();
    if (nextInterval == null) {
      log.info("calculateIfClosed:: nextInterval is null");
      return failed(failureForAbsentTimetable());
    }

    ZonedDateTime dueDateWithOffset = ZonedDateTime.ofInstant(nextInterval
      .getStartTime().toInstant()
      .plusMillis(duration.toMillis()), zone);

    if (nextInterval.getInterval().contains(dueDateWithOffset)) {
      log.info("calculateIfClosed:: nextInterval: {} contains dueDateWithOffset: {}",
        nextInterval, dueDateWithOffset);
      return succeeded(dueDateWithOffset);
    }

    LibraryInterval intervalForDateWithOffset =
      libraryTimetable.findInterval(dueDateWithOffset);
    if (intervalForDateWithOffset == null) {
      log.info("calculateIfClosed:: intervalForDateWithOffset is null");
      return succeeded(libraryTimetable.getTail().getEndTime());
    }
    if (intervalForDateWithOffset.isOpen()) {
      log.info("calculateIfClosed:: intervalForDateWithOffset is open");
      return succeeded(dueDateWithOffset);
    }
    var endTime = intervalForDateWithOffset.getPrevious().getEndTime();
    log.info("calculateIfClosed:: result: {}", endTime);

    return succeeded(endTime);
  }
}
