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

public class EndOfPreviousDayTruncateStrategy extends EndOfPreviousDayStrategy {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public EndOfPreviousDayTruncateStrategy(ZoneId zone) {
    super(zone);
  }

  @Override
  public Result<ZonedDateTime> calculateDueDate(ZonedDateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    log.debug("calculateDueDate:: parameters requestedDate: {}, openingDays: {}",
      requestedDate, openingDays);
    OpeningDay previousDay = openingDays.getPreviousDay();
    if (!previousDay.isOpen()) {
      log.error("calculateDueDate:: previousDay is closed");
      return failed(failureForAbsentTimetable());
    }

    return succeeded(atEndOfDay(openingDays.getPreviousDay().getDate(), zone))
      .next(dateTime -> {
        log.info("calculateDueDate:: result: {}", dateTime);
        return succeeded(dateTime);
      });
  }
}
