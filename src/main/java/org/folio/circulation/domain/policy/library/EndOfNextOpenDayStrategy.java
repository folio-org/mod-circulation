package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.support.results.Result;

public class EndOfNextOpenDayStrategy implements ClosedLibraryStrategy {

  private final ZoneId zone;
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public EndOfNextOpenDayStrategy(ZoneId zone) {
    this.zone = zone;
  }

  @Override
  public Result<ZonedDateTime> calculateDueDate(ZonedDateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    log.debug("calculateDueDate:: parameters requestedDate: {}, openingDays: {}",
      requestedDate, openingDays);
    if (openingDays.getRequestedDay().isOpen()) {
      log.info("calculateDueDate:: requestedDay is open");
      return succeeded(atEndOfDay(requestedDate, zone));
    }
    OpeningDay nextDay = openingDays.getNextDay();
    if (!nextDay.isOpen()) {
      log.error("calculateDueDate:: nextDay is closed");
      return failed(failureForAbsentTimetable());
    }

    return succeeded(atEndOfDay(nextDay.getDate(), zone))
      .next(dateTime -> {
        log.info("calculateDueDate:: result: {}", dateTime);
        return succeeded(dateTime);
      });
  }
}
