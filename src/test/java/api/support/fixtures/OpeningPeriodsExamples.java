package api.support.fixtures;

import api.support.builders.OpeningPeriodsBuilder;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.OpeningPeriod;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class OpeningPeriodsExamples {

  public static final String ONE = "55555555-2f09-4bc9-8924-3734882d44a3";
  public static final String TWO = "66666666-2f09-4bc9-8924-3734882d44a3";
  public static final String THREE = "99999999-2f09-4bc9-8924-3734882d44a3";

  public static final LocalDate DATE_1 = new LocalDate(2019, 10, 25);
  public static final LocalDate DATE_2 = new LocalDate(2019, 10, 26);

  public static final LocalTime OPENING_HOUR_1_START = new LocalTime(7, 0);
  public static final LocalTime OPENING_HOUR_1_END = new LocalTime(12, 0);
  public static final LocalTime OPENING_HOUR_2_START = new LocalTime(14, 30);
  public static final LocalTime OPENING_HOUR_2_END = new LocalTime(18, 30);

  public static final LocalTime OPENING_HOUR_3_START = new LocalTime(8, 30);
  public static final LocalTime OPENING_HOUR_3_END = new LocalTime(13, 0);
  public static final LocalTime OPENING_HOUR_4_START = new LocalTime(14, 00);
  public static final LocalTime OPENING_HOUR_4_END = new LocalTime(17, 30);

  private static final Map<String, OpeningPeriodsBuilder> fakeOpeningPeriods = new HashMap<>();

  static {
    fakeOpeningPeriods.put(ONE, new OpeningPeriodsBuilder(
      Arrays.asList(
        new OpeningPeriod(DATE_1, new OpeningDay(Arrays.asList(
            new OpeningHour(OPENING_HOUR_1_START, OPENING_HOUR_1_END),
            new OpeningHour(OPENING_HOUR_2_START, OPENING_HOUR_2_END)),
          false, true, false)),
        new OpeningPeriod(DATE_2, new OpeningDay(Arrays.asList(
            new OpeningHour(OPENING_HOUR_3_START, OPENING_HOUR_3_END),
            new OpeningHour(OPENING_HOUR_4_START, OPENING_HOUR_4_END)),
          false, true, true))
      )
    ));
  }

  public static OpeningPeriodsBuilder getOpeningPeriodsById(String servicePointId) {
    return fakeOpeningPeriods.get(servicePointId);
  }
}
