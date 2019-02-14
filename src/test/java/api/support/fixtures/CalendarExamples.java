package api.support.fixtures;

import api.support.builders.CalendarBuilder;
import api.support.builders.OpeningDayPeriodBuilder;
import org.folio.circulation.domain.OpeningDayPeriod;
import org.folio.circulation.domain.OpeningHour;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.folio.circulation.domain.OpeningDay.createOpeningDay;
import static org.folio.circulation.domain.OpeningDayPeriod.createDayPeriod;
import static org.folio.circulation.domain.Weekdays.createWeekdays;

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

  public static final LocalDate WEDNESDAY_DATE = new LocalDate(2018, 12, 11);
  public static final LocalDate THURSDAY_DATE = new LocalDate(2018, 12, 12);
  public static final LocalDate FRIDAY_DATE = new LocalDate(2018, 12, 13);

  public static final LocalTime START_TIME_FIRST_PERIOD = new LocalTime(8, 0);
  public static final LocalTime END_TIME_FIRST_PERIOD = new LocalTime(12, 0);

  private static final LocalTime START_TIME_SECOND_PERIOD = new LocalTime(14, 0);
  public static final LocalTime END_TIME_SECOND_PERIOD = new LocalTime(19, 0);

  public static final LocalDate CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY = new LocalDate(2019, 2, 1);
  public static final LocalDate CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY = new LocalDate(2019, 2, 2);
  public static final LocalDate CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY = new LocalDate(2019, 2, 4);


  private static final Map<String, OpeningDayPeriodBuilder> fakeOpeningPeriods = new HashMap<>();

  private CalendarExamples() {
    // not use
  }

  static {
    fakeOpeningPeriods.put(CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED, new OpeningDayPeriodBuilder(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createWeekdays("WEDNESDAY"),
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          WEDNESDAY_DATE, false, true)
      ),
      // current day
      createDayPeriod(
        createWeekdays("THURSDAY"),
        createOpeningDay(new ArrayList<>(), THURSDAY_DATE, false, false)
      ),
      // next day
      createDayPeriod(
        createWeekdays("FRIDAY"),
        createOpeningDay(new ArrayList<>(), FRIDAY_DATE, false, false)
      )));
    fakeOpeningPeriods.put(CASE_WED_THU_FRI_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createWeekdays("WEDNESDAY"),
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          WEDNESDAY_DATE, false, true)
      ),
      // current day
      createDayPeriod(
        createWeekdays("THURSDAY"),
        createOpeningDay(new ArrayList<>(),
          THURSDAY_DATE, false, false)
      ),
      // next day
      createDayPeriod(
        createWeekdays("FRIDAY"),
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          FRIDAY_DATE, false, true)
      )));
    fakeOpeningPeriods.put(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createWeekdays("WEDNESDAY"),
        createOpeningDay(new ArrayList<>(), WEDNESDAY_DATE, true, true)
      ),
      // current day
      createDayPeriod(
        createWeekdays("THURSDAY"),
        createOpeningDay(new ArrayList<>(), THURSDAY_DATE, false, false)
      ),
      // next day
      createDayPeriod(
        createWeekdays("FRIDAY"),
        createOpeningDay(new ArrayList<>(), FRIDAY_DATE, true, true)
      )));
    fakeOpeningPeriods.put(CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createWeekdays("FRIDAY"),
        createOpeningDay(new ArrayList<>(), new LocalDate(2018, 12, 14), true, true)
      ),
      // current day
      createDayPeriod(
        createWeekdays("SATURDAY"),
        createOpeningDay(new ArrayList<>(), new LocalDate(2018, 12, 15), false, false)
      ),
      // next day
      createDayPeriod(
        createWeekdays("MONDAY"),
        createOpeningDay(new ArrayList<>(), new LocalDate(2018, 12, 17), true, true)
      )));
    fakeOpeningPeriods.put(CASE_FRI_SAT_MON_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_FRI_SAT_MON_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createWeekdays("FRIDAY"),
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY, false, true)
      ),
      // current day
      createDayPeriod(
        createWeekdays("SATURDAY"),
        createOpeningDay(new ArrayList<>(), CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, false, false)
      ),
      // next day
      createDayPeriod(
        createWeekdays("MONDAY"),
        createOpeningDay(Arrays.asList(new OpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), new OpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, false, true)
      )));
  }

  public static CalendarBuilder getCalendarById(String serviceId) {
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
        return new CalendarBuilder(serviceId, "Default calendar");
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
