package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.function.BiPredicate;

public class EndOfPreviousDayStrategy implements ClosedLibraryStrategy {

  private final BiPredicate<DateTime, AdjustingOpeningDays> libraryIsOpenPredicate;
  private final DateTimeZone zone;

  public EndOfPreviousDayStrategy(
    BiPredicate<DateTime, AdjustingOpeningDays> libraryIsOpenPredicate,
    DateTimeZone zone) {
    this.libraryIsOpenPredicate = libraryIsOpenPredicate;
    this.zone = zone;
  }

  @Override

  public DateTime calculateDueDate(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays) {
    if (libraryIsOpenPredicate.test(requestedDate, adjustingOpeningDays)) {
      return requestedDate;
    }
    OpeningDay prevDayPeriod = adjustingOpeningDays.getPreviousDay();
    return ClosedLibraryStrategyUtils.getTermDueDate(prevDayPeriod, zone);
  }
}
