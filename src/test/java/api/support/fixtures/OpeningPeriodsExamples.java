package api.support.fixtures;

import static api.support.fixtures.OpeningHourExamples.afternoon;
import static api.support.fixtures.OpeningHourExamples.morning;
import static java.time.ZoneOffset.UTC;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;

import org.folio.circulation.domain.OpeningDay;

import api.support.OpeningPeriod;
import api.support.builders.OpeningPeriodsBuilder;

public class OpeningPeriodsExamples {
  public static OpeningPeriodsBuilder oneDayPeriod() {
    return new OpeningPeriodsBuilder(Arrays.asList(
      new OpeningPeriod(ZonedDateTime.of(2020, 1, 22, 0, 0, 0, 0, UTC).toLocalDate(),
        OpeningDay.createOpeningDay(Arrays.asList(morning(), afternoon()),
          LocalDate.of(2020, 1, 22), false, true, UTC))));
  }
}
