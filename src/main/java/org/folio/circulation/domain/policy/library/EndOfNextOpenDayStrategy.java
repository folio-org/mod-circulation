package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.util.Objects;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;

public class EndOfNextOpenDayStrategy implements ClosedLibraryStrategy {
  private final DateTimeZone zone;

  public EndOfNextOpenDayStrategy(DateTimeZone zone) {
    this.zone = zone;
  }

  @Override
  public DateTime calculateDueDate(DateTime requestedDate, AdjustingOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    if (openingDays.getRequestedDay().getOpen()) {
      return requestedDate.withZone(zone).withTime(END_OF_A_DAY);
    }
    OpeningDay nextDay = openingDays.getNextDay();
    LocalDate localDate = nextDay.getDate();
    return localDate.toDateTime(END_OF_A_DAY, zone);
  }
}
