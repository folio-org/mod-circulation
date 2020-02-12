package api.support.fixtures;

import static api.support.fixtures.OpeningHourExamples.morning;
import static api.support.fixtures.OpeningHourExamples.afternoon;

import java.util.Arrays;

import api.support.OpeningPeriod;
import org.folio.circulation.domain.OpeningDay;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import api.support.builders.OpeningPeriodsBuilder;
import org.joda.time.LocalDate;

public class OpeningPeriodsExamples {
  public static OpeningPeriodsBuilder oneDayPeriod() {
    return new OpeningPeriodsBuilder(Arrays.asList(
      new OpeningPeriod(new DateTime(2020, 1, 22, 0, 0, 0, DateTimeZone.UTC).toLocalDate(),
        OpeningDay.createOpeningDay(Arrays.asList(morning(), afternoon()),
          new LocalDate(2020, 1, 22), false, true))));
  }
}
