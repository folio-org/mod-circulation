package api.support.fixtures;

import api.support.builders.CalendarBuilder;
import api.support.builders.OpeningDayPeriodBuilder;
import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import api.support.OpeningDayPeriod;
import org.folio.circulation.domain.OpeningHour;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.folio.circulation.domain.OpeningDay.createOpeningDay;
import static api.support.OpeningDayPeriod.createDayPeriod;

public class CalendarExamples {

  public static final String CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID = "11111111-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_FRI_SAT_MON_SERVICE_POINT_ID = "22222222-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID = "33333333-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_WED_THU_FRI_SERVICE_POINT_ID = "44444444-2f09-4bc9-8924-3734882d44a3";

  public static final String CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED = "85346678-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID = "66655555-2f09-4bc9-8924-3734882d44a3";

  static final String CASE_START_DATE_MONTHS_AGO_AND_END_DATE_THU = "12345698-2f09-4bc9-8924-3734882d44a3";

  static final String CASE_START_DATE_MONTHS_AGO_AND_END_DATE_WED = "77777777-2f09-4bc9-8924-3734882d44a3";
  static final String CASE_START_DATE_FRI_AND_END_DATE_NEXT_MONTHS = "88888888-2f09-4bc9-8924-3734882d44a3";

  public static final String CASE_CURRENT_IS_OPEN = "7a50ce1e-ce47-4841-a01f-fd771ff3da1b";
  public static final LocalDate CASE_CURRENT_IS_OPEN_PREV_DAY = new LocalDate(2019, 2, 4);
  public static final LocalDate CASE_CURRENT_IS_OPEN_CURR_DAY = new LocalDate(2019, 2, 5);
  public static final LocalDate CASE_CURRENT_IS_OPEN_NEXT_DAY = new LocalDate(2019, 2, 6);

  public static final LocalDate WEDNESDAY_DATE = new LocalDate(2018, 12, 11);
  public static final LocalDate THURSDAY_DATE = new LocalDate(2018, 12, 12);
  public static final LocalDate FRIDAY_DATE = new LocalDate(2018, 12, 13);

  public static final LocalTime START_TIME_FIRST_PERIOD = new LocalTime(8, 0);
  public static final LocalTime END_TIME_FIRST_PERIOD = new LocalTime(12, 0);

  public static final LocalTime START_TIME_SECOND_PERIOD = new LocalTime(14, 0);
  public static final LocalTime END_TIME_SECOND_PERIOD = new LocalTime(19, 0);

  public static final LocalDate CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY = new LocalDate(2019, 2, 1);
  public static final LocalDate CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY = new LocalDate(2019, 2, 2);
  public static final LocalDate CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY = new LocalDate(2019, 2, 4);

  public static final LocalDate CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE = new LocalDate(2018, 12, 14);
  public static final LocalDate CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE = new LocalDate(2018, 12, 15);
  public static final LocalDate CASE_FRI_SAT_MON_DAY_ALL_NEXT_DATE = new LocalDate(2018, 12, 17);

  private static final String REQUESTED_DATE_PARAM = "requestedDate";

  private static final Map<String, OpeningDayPeriodBuilder> fakeOpeningPeriods = new HashMap<>();

  private CalendarExamples() {
    // not use
  }

  static {
    fakeOpeningPeriods.put(CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED, new OpeningDayPeriodBuilder(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          WEDNESDAY_DATE, false, true)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), THURSDAY_DATE, false, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), FRIDAY_DATE, false, false)
      )));
    fakeOpeningPeriods.put(CASE_WED_THU_FRI_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          WEDNESDAY_DATE, false, true)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(),
          THURSDAY_DATE, false, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          FRIDAY_DATE, false, true)
      )));
    fakeOpeningPeriods.put(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), WEDNESDAY_DATE, true, true)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), THURSDAY_DATE, false, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), FRIDAY_DATE, true, true)
      )));
    fakeOpeningPeriods.put(CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE, true, true)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE, false, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), CASE_FRI_SAT_MON_DAY_ALL_NEXT_DATE, true, true)
      )));
    fakeOpeningPeriods.put(CASE_FRI_SAT_MON_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_FRI_SAT_MON_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY, false, true)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, false, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, false, true)
      )));
    fakeOpeningPeriods.put(CASE_CURRENT_IS_OPEN, new OpeningDayPeriodBuilder(CASE_CURRENT_IS_OPEN,
      // prev day
      createDayPeriod(
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          CASE_CURRENT_IS_OPEN_PREV_DAY, false, true)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          CASE_CURRENT_IS_OPEN_CURR_DAY, false, true)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          CASE_CURRENT_IS_OPEN_NEXT_DAY, false, true)
      )));
  }

  private static OpeningDayPeriodBuilder buildAllDayOpenCalenderResponse(LocalDate requestedDate, String servicePointId) {
    return new OpeningDayPeriodBuilder(servicePointId,
      createDayPeriod(
        createOpeningDay(Collections.emptyList(), requestedDate.minusDays(1), true, true)
      ),
      createDayPeriod(
        createOpeningDay(Collections.emptyList(), requestedDate, true, true)
      ),
      createDayPeriod(
        createOpeningDay(Collections.emptyList(), requestedDate.plusDays(1), true, true)
      )
    );
  }

  public static CalendarBuilder getCalendarById(String serviceId) {
    return getCalendarById(serviceId,
      new CaseInsensitiveHeaders().add(REQUESTED_DATE_PARAM, "2019-01-01"));
  }

  public static CalendarBuilder getCalendarById(String serviceId, MultiMap queries) {
    switch (serviceId) {
      case CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED:
        return new CalendarBuilder(fakeOpeningPeriods.get(serviceId));

      case CASE_FRI_SAT_MON_SERVICE_POINT_ID:
        return new CalendarBuilder(fakeOpeningPeriods.get(serviceId));

      case CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID:
        return new CalendarBuilder(fakeOpeningPeriods.get(serviceId));

      case CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID:
        return new CalendarBuilder(fakeOpeningPeriods.get(serviceId));

      case CASE_WED_THU_FRI_SERVICE_POINT_ID:
        return new CalendarBuilder(fakeOpeningPeriods.get(serviceId));

      case CASE_CURRENT_IS_OPEN:
        return new CalendarBuilder(fakeOpeningPeriods.get(serviceId));

      case CASE_START_DATE_MONTHS_AGO_AND_END_DATE_THU:
        DateTime endDate = THURSDAY_DATE.toDateTime(LocalTime.MIDNIGHT);
        DateTime startDate = endDate.minusMonths(1);
        return new CalendarBuilder(CASE_START_DATE_MONTHS_AGO_AND_END_DATE_THU,
          startDate, endDate);

      case CASE_START_DATE_MONTHS_AGO_AND_END_DATE_WED:
        DateTime endDateWednesday = WEDNESDAY_DATE.toDateTime(LocalTime.MIDNIGHT);
        DateTime startDateWednesday = endDateWednesday.minusMonths(1);
        return new CalendarBuilder(CASE_START_DATE_MONTHS_AGO_AND_END_DATE_THU,
          startDateWednesday, endDateWednesday);

      case CASE_START_DATE_FRI_AND_END_DATE_NEXT_MONTHS:
        DateTime startDateFriday = FRIDAY_DATE.toDateTime(LocalTime.MIDNIGHT);
        DateTime endDateFriday = startDateFriday.plusMonths(1);
        return new CalendarBuilder(CASE_START_DATE_MONTHS_AGO_AND_END_DATE_THU,
          startDateFriday, endDateFriday);

      default:
        LocalDate requestedDate = LocalDate.parse(queries.get(REQUESTED_DATE_PARAM));
        return new CalendarBuilder(buildAllDayOpenCalenderResponse(requestedDate, serviceId));
    }
  }

  public static List<OpeningDayPeriod> getCurrentAndNextFakeOpeningDayByServId(String serviceId) {
    OpeningDayPeriodBuilder periodBuilder = fakeOpeningPeriods.get(serviceId);
    return Arrays.asList(periodBuilder.getCurrentPeriod(), periodBuilder.getLastPeriod());
  }

  public static OpeningDayPeriod getFirstFakeOpeningDayByServId(String serviceId) {
    return fakeOpeningPeriods.get(serviceId).getFirstPeriod();
  }

  public static OpeningDayPeriod getCurrentFakeOpeningDayByServId(String serviceId) {
    return fakeOpeningPeriods.get(serviceId).getCurrentPeriod();
  }

  public static OpeningDayPeriod getLastFakeOpeningDayByServId(String serviceId) {
    return fakeOpeningPeriods.get(serviceId).getLastPeriod();
  }

  public static List<OpeningDayPeriod> getFakeOpeningDayByServId(String serviceId) {
    OpeningDayPeriodBuilder periodBuilder = fakeOpeningPeriods.get(serviceId);
    return Arrays.asList(periodBuilder.getFirstPeriod(), periodBuilder.getCurrentPeriod(), periodBuilder.getLastPeriod());
  }
}
