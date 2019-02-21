package api.support.fixtures;

import api.support.builders.CalendarBuilder;
import api.support.builders.OpeningDayPeriodBuilder;
import org.folio.circulation.domain.OpeningDayPeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.folio.circulation.domain.OpeningDay.createOpeningDay;
import static org.folio.circulation.domain.OpeningDayPeriod.createDayPeriod;
import static org.folio.circulation.domain.OpeningHour.createOpeningHour;
import static org.folio.circulation.resources.CheckOutByBarcodeResource.DATE_TIME_FORMATTER;

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

  public static final String WEDNESDAY_DATE = "2018-12-11T00:00:00.000+0000";
  public static final String THURSDAY_DATE = "2018-12-12T00:00:00.000+0000";
  public static final String FRIDAY_DATE = "2018-12-13T00:00:00.000+0000";

  public static final String START_TIME_FIRST_PERIOD = "08:00";
  private static final String END_TIME_FIRST_PERIOD = "12:00";

  private static final String START_TIME_SECOND_PERIOD = "14:00";
  public static final String END_TIME_SECOND_PERIOD = "19:00";

  public static final String CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY = "2019-02-01T00:00:00.000+0000";
  public static final String CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY = "2019-02-02T00:00:00.000+0000";
  public static final String CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY = "2019-02-04T00:00:00.000+0000";


  private static final Map<String, OpeningDayPeriodBuilder> fakeOpeningPeriods = new HashMap<>();

  private CalendarExamples() {
    // not use
  }

  static {
    fakeOpeningPeriods.put(CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED, new OpeningDayPeriodBuilder(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(Arrays.asList(createOpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), createOpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          WEDNESDAY_DATE, false, true, false)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), THURSDAY_DATE, false, false, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), FRIDAY_DATE, false, false, false)
      )));
    fakeOpeningPeriods.put(CASE_WED_THU_FRI_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(Arrays.asList(createOpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), createOpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          WEDNESDAY_DATE, false, true, false)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(Arrays.asList(createOpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), createOpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          THURSDAY_DATE, false, true, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(Arrays.asList(createOpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), createOpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          FRIDAY_DATE, false, true, false)
      )));
    fakeOpeningPeriods.put(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), WEDNESDAY_DATE, true, true, false)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), THURSDAY_DATE, true, true, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), FRIDAY_DATE, true, true, false)
      )));
    fakeOpeningPeriods.put(CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), "2018-12-14T00:00:00.000+0000", true, true, false)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), "2018-12-15T00:00:00.000+0000", false, false, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), "2018-12-17T00:00:00.000+0000", true, true, false)
      )));
    fakeOpeningPeriods.put(CASE_FRI_SAT_MON_SERVICE_POINT_ID, new OpeningDayPeriodBuilder(CASE_FRI_SAT_MON_SERVICE_POINT_ID,
      // prev day
      createDayPeriod(
        createOpeningDay(Arrays.asList(createOpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), createOpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY, false, true, false)
      ),
      // current day
      createDayPeriod(
        createOpeningDay(new ArrayList<>(), CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, false, false, false)
      ),
      // next day
      createDayPeriod(
        createOpeningDay(Arrays.asList(createOpeningHour(START_TIME_FIRST_PERIOD, END_TIME_FIRST_PERIOD), createOpeningHour(START_TIME_SECOND_PERIOD, END_TIME_SECOND_PERIOD)),
          CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, false, true, false)
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
        LocalDate localThursdayDate = LocalDate.parse(THURSDAY_DATE, DATE_TIME_FORMATTER);
        LocalDateTime endDate = localThursdayDate.atTime(LocalTime.MIN);
        LocalDateTime startDate = endDate.minusMonths(1);
        return new CalendarBuilder(CASE_START_DATE_MONTHS_AGO_AND_END_DATE_THU,
          startDate, endDate);

      case CASE_START_DATE_MONTHS_AGO_AND_END_DATE_WED:
        LocalDate localWednesdayDate = LocalDate.parse(WEDNESDAY_DATE, DATE_TIME_FORMATTER);
        LocalDateTime endDateWednesday = localWednesdayDate.atTime(LocalTime.MIN);
        LocalDateTime startDateWednesday = endDateWednesday.minusMonths(1);
        return new CalendarBuilder(CASE_START_DATE_MONTHS_AGO_AND_END_DATE_THU,
          startDateWednesday, endDateWednesday);

      case CASE_START_DATE_FRI_AND_END_DATE_NEXT_MONTHS:
        LocalDate localFridayDate = LocalDate.parse(FRIDAY_DATE, DATE_TIME_FORMATTER);
        LocalDateTime startDateFriday = localFridayDate.atTime(LocalTime.MIN);
        LocalDateTime endDateFriday = startDateFriday.plusMonths(1);
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
