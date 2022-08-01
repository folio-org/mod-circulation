package api.support.fixtures;

import static api.support.fixtures.OpeningHourExamples.afternoon;
import static api.support.fixtures.OpeningHourExamples.morning;
import static java.time.ZoneOffset.UTC;

import api.support.builders.OpeningDayCollectionBuilder;
import java.time.LocalDate;
import java.util.Arrays;
import org.folio.circulation.domain.OpeningDay;

public class OpeningDayCollectionExamples {

  public static OpeningDayCollectionBuilder oneDayPeriod() {
    return new OpeningDayCollectionBuilder(
      Arrays.asList(
        new OpeningDay(
          Arrays.asList(morning(), afternoon()),
          LocalDate.of(2020, 1, 22),
          false,
          true,
          UTC
        )
      )
    );
  }
}
