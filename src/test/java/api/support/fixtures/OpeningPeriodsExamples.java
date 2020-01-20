package api.support.fixtures;

import api.support.builders.OpeningPeriodsBuilder;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.OpeningPeriod;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OpeningPeriodsExamples {

  public static final String CASE_TWO_OPENING_DAYS_SERVICE_ID = "11111111-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_ALL_DAY_OPENINGS_SERVICE_ID = "22222222-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_NO_OPENING_HOURS_SERVICE_ID = "33333333-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_NO_OPENING_DAYS_SERVICE_ID = "44444444-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_ERROR_400_SERVICE_ID = "55555555-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_ERROR_404_SERVICE_ID = "66666666-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_ERROR_500_SERVICE_ID = "77777777-2f09-4bc9-8924-3734882d44a3";

  public static final LocalDate MAY_FIRST = new LocalDate(2019, 5, 1);
  public static final LocalDate MAY_SECOND = new LocalDate(2019, 5, 2);

  public static final LocalTime OPENING_HOUR_1_START = new LocalTime(7, 0);
  public static final LocalTime OPENING_HOUR_1_END = new LocalTime(12, 0);
  public static final LocalTime OPENING_HOUR_2_START = new LocalTime(13, 30);
  public static final LocalTime OPENING_HOUR_2_END = new LocalTime(18, 30);
  public static final LocalTime ALL_DAY_OPENING_START = new LocalTime(0, 0);
  public static final LocalTime ALL_DAY_OPENING_END = new LocalTime(23, 59);

  private static final Map<String, OpeningPeriodsBuilder> fakeOpeningPeriods = new HashMap<>();

  static {
    fakeOpeningPeriods.put(CASE_TWO_OPENING_DAYS_SERVICE_ID, new OpeningPeriodsBuilder(Arrays.asList(
        new OpeningPeriod(MAY_FIRST, new OpeningDay(Arrays.asList(
            new OpeningHour(OPENING_HOUR_1_START, OPENING_HOUR_1_END),
            new OpeningHour(OPENING_HOUR_2_START, OPENING_HOUR_2_END)),
          false, true, false)),
        new OpeningPeriod(MAY_SECOND, new OpeningDay(Arrays.asList(
            new OpeningHour(OPENING_HOUR_1_START, OPENING_HOUR_1_END),
            new OpeningHour(OPENING_HOUR_2_START, OPENING_HOUR_2_END)),
          false, true, false)))));

    fakeOpeningPeriods.put(CASE_ALL_DAY_OPENINGS_SERVICE_ID, new OpeningPeriodsBuilder(Arrays.asList(
      new OpeningPeriod(MAY_FIRST, new OpeningDay(Collections.singletonList(
        new OpeningHour(ALL_DAY_OPENING_START, ALL_DAY_OPENING_END)),
        true, true, false)),
      new OpeningPeriod(MAY_SECOND, new OpeningDay(Collections.singletonList(
        new OpeningHour(ALL_DAY_OPENING_START, ALL_DAY_OPENING_END)),
        true, true, false)))));

    fakeOpeningPeriods.put(CASE_NO_OPENING_HOURS_SERVICE_ID, new OpeningPeriodsBuilder(Arrays.asList(
      new OpeningPeriod(MAY_FIRST, new OpeningDay(Collections.emptyList(), false, true, false)),
      new OpeningPeriod(MAY_SECOND, new OpeningDay(Collections.emptyList(),false, true, true)))));

    fakeOpeningPeriods.put(CASE_NO_OPENING_DAYS_SERVICE_ID, new OpeningPeriodsBuilder(Collections.emptyList()));
  }

  public static OpeningPeriodsBuilder getOpeningPeriodsById(String servicePointId) {
    return fakeOpeningPeriods.get(servicePointId);
  }
}
