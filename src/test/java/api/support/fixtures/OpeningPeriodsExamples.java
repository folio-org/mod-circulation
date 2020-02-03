package api.support.fixtures;

import static api.support.fixtures.OpeningHourExamples.afterNoon;
import static api.support.fixtures.OpeningHourExamples.beforeNoon;

import java.util.Arrays;

import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningPeriod;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import api.support.builders.OpeningPeriodsBuilder;

public class OpeningPeriodsExamples {
  public static OpeningPeriodsBuilder oneDayPeriod() {
    return new OpeningPeriodsBuilder(Arrays.asList(
      new OpeningPeriod(new DateTime(2020, 1, 22, 0, 0, 0, DateTimeZone.UTC).toLocalDate(),
        new OpeningDay(Arrays.asList(beforeNoon(), afterNoon()),false, true, false))));
  }
}
