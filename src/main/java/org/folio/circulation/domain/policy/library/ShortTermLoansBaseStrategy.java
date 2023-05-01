package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.results.Result;

public abstract class ShortTermLoansBaseStrategy implements ClosedLibraryStrategy {

  protected final ZoneId zone;

  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  protected ShortTermLoansBaseStrategy(ZoneId zone) {
    this.zone = zone;
  }

  @Override
  public Result<ZonedDateTime> calculateDueDate(ZonedDateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    log.debug("calculateDueDate:: parameters requestedDate: {}, openingDays: {}",
      requestedDate, openingDays);
    log.info("----- ShortTermLoansBaseStrategy -----");
    LibraryTimetable libraryTimetable =
      LibraryTimetableConverter.convertToLibraryTimetable(openingDays, zone);

    log.info("libraryTimetable head:{}",libraryTimetable.getHead().getStartTime());

    LibraryInterval requestedInterval = libraryTimetable.findInterval(requestedDate);
    if (requestedInterval == null) {
      log.error("calculateDueDate:: requestedInterval is null");
      return failed(failureForAbsentTimetable());
    }
    if (requestedInterval.isOpen()) {
      log.info("calculateDueDate:: requestedInterval is open");
      return succeeded(requestedDate);
    }
    log.info("requestedInterval is close so going as per strategy");
    return calculateIfClosed(libraryTimetable, requestedInterval)
      .next(dateTime -> {
        log.info("calculateDueDate:: result: {}", dateTime);
        return succeeded(dateTime);
      });
  }

  protected abstract Result<ZonedDateTime> calculateIfClosed(
    LibraryTimetable libraryTimetable, LibraryInterval requestedInterval);
}
