package api.loans;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningDayPeriod;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.domain.policy.LoansPolicyProfile;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.fixtures.CalendarExamples.*;
import static org.folio.circulation.resources.CheckOutByBarcodeResource.DATE_TIME_FORMATTER;
import static org.folio.circulation.support.PeriodUtil.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.HOURS_PER_DAY;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;

/**
 * Test case for Short-term loans
 * Loanable = Y
 * Loan profile = Rolling
 * Closed Library Due Date Management = Move to the beginning of the next open service point hours
 * <p>
 * Expected result:
 * If SPID-1 is determined to be CLOSED for system-calculated due date and timestamp
 * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
 */
public class CheckOutCalculateOffsetTimeTests extends APITests {

  private static final String LOAN_POLICY_NAME = "Move to the beginning of the next open service point hours";
  private static final String POLICY_PROFILE_NAME = LoansPolicyProfile.ROLLING.name();
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";
  private static final String OFFSET_INTERVAL_HOURS = "Hours";
  private static final String OFFSET_INTERVAL_MINUTES = "Minutes";

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: closed
   * Next day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testOffsetIntervalHoursIfCurrentDayIsClosed() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = 1;
    int offsetDuration = new SplittableRandom().nextInt(0, 23);

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getExpectedDateTimeOfOpeningAllDay(dayPeriod, OFFSET_INTERVAL_HOURS, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration, OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Minutes
   * Current day: closed
   * Next day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testOffsetIntervalMinutesIfCurrentDayIsClosed() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = 1;
    int offsetDuration = new SplittableRandom().nextInt(0, MINUTES_PER_HOUR);

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getExpectedDateTimeOfOpeningAllDay(dayPeriod, OFFSET_INTERVAL_MINUTES, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration, OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: closed
   * Next day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testOffsetIntervalHoursIfCurrentDayIsClosedWithPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, 24);
    int offsetDuration = new SplittableRandom().nextInt(0, 4);

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getExpectedDateTimeOfPeriodDay(dayPeriod, OFFSET_INTERVAL_HOURS, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration, OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  private DateTime getExpectedDateTimeOfPeriodDay(OpeningDayPeriod dayPeriod, String offsetInterval, int offsetDuration) {
    OpeningDay openingDay = dayPeriod.getOpeningDay();
    LocalTime startTime = LocalTime.parse(openingDay.getOpeningHour().get(0).getStartTime());
    return getExpectedDateTimeOfOpeningDay(dayPeriod, startTime, offsetInterval, offsetDuration);
  }

  /**
   * Loan period: Minutes
   * Offset period: Minutes
   * Current day: closed
   * Next day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testOffsetIntervalMinutesIfCurrentDayIsClosedWithPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, MINUTES_PER_HOUR);
    int offsetDuration = new SplittableRandom().nextInt(0, MINUTES_PER_HOUR);

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getExpectedDateTimeOfPeriodDay(dayPeriod, OFFSET_INTERVAL_MINUTES, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_MINUTES, duration, OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: open allDay
   * Next day: open allDay
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testOffsetIntervalHoursIfCurrentAllDayOpen() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    int duration = 1;
    int offsetDuration = new SplittableRandom().nextInt(0, 6);

    // current day
    OpeningDayPeriod dayPeriod = getCurrentFakeOpeningDayByServId(servicePointId);
    LocalTime time = LocalTime.now(ZoneOffset.UTC);
    DateTime expectedDueDate = getExpectedDateTimeOfOpeningDay(dayPeriod, time, INTERVAL_HOURS, duration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration, OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Minutes
   * Offset period: Minutes
   * Current day: open allDay
   * Next day: open allDay
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testOffsetIntervalMinutesIfCurrentAllDayOpen() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    int duration = 15;
    int offsetDuration = new SplittableRandom().nextInt(0, MINUTES_PER_HOUR);

    // current day
    OpeningDayPeriod dayPeriod = getCurrentFakeOpeningDayByServId(servicePointId);
    LocalTime time = LocalTime.now(ZoneOffset.UTC);
    DateTime expectedDueDate = getExpectedDateTimeOfOpeningDay(dayPeriod, time, INTERVAL_MINUTES, duration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_MINUTES, duration, OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testOffsetIntervalHoursForPeriod() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration = 1;
    int offsetDuration = 0;

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getExpectedDateTimeFromPeriod(openingDays, INTERVAL_HOURS, duration, OFFSET_INTERVAL_HOURS, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration, OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testOffsetIntervalHoursForPeriodCase1() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration;
    int offsetDuration = 0;

    LocalTime endTimeOfPeriod = LocalTime.parse(END_TIME_SECOND_PERIOD);
    LocalTime timeNow = LocalTime.now(ZoneOffset.UTC);

    // The value of `duration` is calculated taking into account the exit for the period.
    if (timeNow.isBefore(endTimeOfPeriod)) {
      duration = (int) ChronoUnit.HOURS.between(timeNow, endTimeOfPeriod) + 1;
    } else {
      duration = HOURS_PER_DAY - (int) ChronoUnit.HOURS.between(endTimeOfPeriod, timeNow) + 1;
    }

    OpeningDay openingDay = getLastFakeOpeningDayByServId(servicePointId).getOpeningDay();
    LocalDate expectedDate = LocalDate.parse(openingDay.getDate(), DATE_TIME_FORMATTER);
    LocalTime expectedTime = LocalTime.parse(START_TIME_FIRST_PERIOD).plusHours(offsetDuration);

    DateTime expectedDueDate = timeZoneWrapper(LocalDateTime.of(expectedDate, expectedTime));
    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration, OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testOffsetIntervalHoursForPeriodCase2() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration;
    int offsetDuration = 1;

    LocalTime starTmeOfPeriod = LocalTime.parse(START_TIME_FIRST_PERIOD);
    LocalTime timeNow = LocalTime.now(ZoneOffset.UTC);

    // The value is calculated taking into account the transition to the next period
    if (timeNow.isAfter(starTmeOfPeriod)) {
      duration = HOURS_PER_DAY - (int) ChronoUnit.HOURS.between(starTmeOfPeriod, timeNow) - 1;
    } else {
      duration = (int) ChronoUnit.HOURS.between(timeNow, starTmeOfPeriod) - 1;
    }

    OpeningDay openingDay = getLastFakeOpeningDayByServId(servicePointId).getOpeningDay();
    LocalDate expectedDate = LocalDate.parse(openingDay.getDate(), DATE_TIME_FORMATTER);
    LocalTime expectedTime = LocalTime.parse(START_TIME_FIRST_PERIOD).plusHours(offsetDuration);

    DateTime expectedDueDate = timeZoneWrapper(LocalDateTime.of(expectedDate, expectedTime));
    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration, OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Minutes
   * Offset period: Minutes
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testOffsetIntervalMinutesForPeriod() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, MINUTES_PER_HOUR);
    int offsetDuration = new SplittableRandom().nextInt(0, MINUTES_PER_HOUR);

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getExpectedDateTimeFromPeriod(openingDays, INTERVAL_MINUTES, duration, OFFSET_INTERVAL_MINUTES, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_MINUTES, duration, OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Minutes
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testOffsetIntervalMinutesCurrentDay() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration = 1;
    int offsetDuration = new SplittableRandom().nextInt(0, MINUTES_PER_HOUR);

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getExpectedDateTimeFromPeriod(openingDays, INTERVAL_HOURS, duration, OFFSET_INTERVAL_MINUTES, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration, OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Minutes
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=closed, FRI=closed
   */
  @Test
  public void testOffsetIntervalMinutesWhenCurrentAndNextDayIsClosed() throws Exception {
    String servicePointId = CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED;
    int duration = 1;
    int offsetDuration = new SplittableRandom().nextInt(0, MINUTES_PER_HOUR);

    OpeningDayPeriod dayPeriod = getFirstFakeOpeningDayByServId(servicePointId);
    LocalDate expectedDate = LocalDate.parse(dayPeriod.getOpeningDay().getDate(), DATE_TIME_FORMATTER);
    LocalTime expectedTime = LocalTime.parse(START_TIME_FIRST_PERIOD).plusMinutes(offsetDuration);
    DateTime expectedDueDate = timeZoneWrapper(LocalDateTime.of(expectedDate, expectedTime));

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration, OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Check result
   */
  private void checkOffsetTime(DateTime expectedDueDate, String servicePointId,
                               String interval, int duration,
                               String offsetInterval, int offsetDuration)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry = createLoanPolicyOffsetTimeEntry(duration, interval, offsetDuration, offsetInterval);
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime actualDueDate = getThresholdDateTime(DateTime.parse(loan.getString("dueDate")));
    DateTime thresholdDateTime = getThresholdDateTime(expectedDueDate);

    assertThat("due date should be " + thresholdDateTime + ", actual due date is " + actualDueDate,
      actualDueDate.compareTo(thresholdDateTime) == 0);
  }

  /**
   * Minor threshold when comparing minutes or milliseconds of dateTime
   */
  private DateTime getThresholdDateTime(DateTime dateTime) {
    return dateTime.withSecondOfMinute(0).withMillisOfSecond(0);
  }

  private DateTime getExpectedDateTimeFromPeriod(List<OpeningDayPeriod> openingDays,
                                                 String interval, int duration,
                                                 String offsetInterval, int offsetDuration) {

    OpeningDay currentOpeningDay = openingDays.get(0).getOpeningDay();
    OpeningDay nextOpeningDay = openingDays.get(1).getOpeningDay();

    LocalDate dateOfCurrentDay = LocalDate.parse(currentOpeningDay.getDate(), DATE_TIME_FORMATTER);
    LocalDate dateOfNextDay = LocalDate.parse(nextOpeningDay.getDate(), DATE_TIME_FORMATTER);

    List<OpeningHour> currentDayPeriod = currentOpeningDay.getOpeningHour();
    List<OpeningHour> nextDayPeriod = nextOpeningDay.getOpeningHour();

    LoanPolicyPeriod period = interval.equals(INTERVAL_HOURS)
      ? LoanPolicyPeriod.HOURS
      : LoanPolicyPeriod.MINUTES;

    LoanPolicyPeriod offsetPeriod = offsetInterval.equals(INTERVAL_HOURS)
      ? LoanPolicyPeriod.HOURS
      : LoanPolicyPeriod.MINUTES;

    LocalTime timeOfCurrentDay = LocalTime.now(ZoneOffset.UTC);
    LocalTime timeShift = getTimeShift(timeOfCurrentDay, period, duration);

    if (isDateTimeWithDurationInsideDay(currentOpeningDay, timeShift)) {
      if (isInPeriodOpeningDay(currentDayPeriod, timeShift)) {
        return calculateOffset(currentOpeningDay, dateOfCurrentDay, timeShift, LoanPolicyPeriod.INCORRECT, 0);
      }
      LocalTime startTimeOfNextPeriod = findStartTime(currentDayPeriod, timeShift);
      return calculateOffset(currentOpeningDay, dateOfCurrentDay, startTimeOfNextPeriod, offsetPeriod, offsetDuration);
    } else {
      if (isInPeriodOpeningDay(nextDayPeriod, timeShift)) {
        return calculateOffset(nextOpeningDay, dateOfNextDay, timeShift, LoanPolicyPeriod.INCORRECT, 0);
      }
      LocalTime startTimeOfNextPeriod = findStartTime(nextDayPeriod, timeShift);
      return calculateOffset(nextOpeningDay, dateOfNextDay, startTimeOfNextPeriod, offsetPeriod, offsetDuration);
    }
  }

  private DateTime calculateOffset(OpeningDay openingDay, LocalDate date, LocalTime time,
                                   LoanPolicyPeriod offsetInterval, int offsetDuration) {

    LocalDateTime dateTime = LocalDateTime.of(date, time);
    List<OpeningHour> openingHours = openingDay.getOpeningHour();
    switch (offsetInterval) {
      case HOURS:
        LocalTime offsetTime = time.plusHours(offsetDuration);
        return getDateTimeOffsetInPeriod(openingHours, date, offsetTime);
      case MINUTES:
        offsetTime = time.plusMinutes(offsetDuration);
        return getDateTimeOffsetInPeriod(openingHours, date, offsetTime);
      default:
        return timeZoneWrapper(dateTime);
    }
  }

  private DateTime getDateTimeOffsetInPeriod(List<OpeningHour> openingHour, LocalDate date, LocalTime offsetTime) {
    if (isInPeriodOpeningDay(openingHour, offsetTime)) {
      return timeZoneWrapper(LocalDateTime.of(date, offsetTime));
    }

    LocalTime endTimeOfPeriod = findEndTime(openingHour, offsetTime);
    return timeZoneWrapper(LocalDateTime.of(date, endTimeOfPeriod));
  }

  private LocalTime findStartTime(List<OpeningHour> openingHoursList, LocalTime time) {
    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = LocalTime.parse(openingHoursList.get(i).getStartTime());
      LocalTime startTimeSecond = LocalTime.parse(openingHoursList.get(i + 1).getStartTime());
      if (time.isAfter(startTimeFirst) && time.isBefore(startTimeSecond)) {
        return startTimeSecond;
      }
    }
    return time;
  }

  private LocalTime findEndTime(List<OpeningHour> openingHoursList, LocalTime time) {
    LocalTime endTimePeriod = LocalTime.parse(openingHoursList.get(openingHoursList.size() - 1).getEndTime());
    if (time.isAfter(endTimePeriod)) {
      return endTimePeriod;
    }
    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = LocalTime.parse(openingHoursList.get(i).getStartTime());
      LocalTime endTimeFirst = LocalTime.parse(openingHoursList.get(i).getEndTime());
      LocalTime startTimeSecond = LocalTime.parse(openingHoursList.get(i + 1).getStartTime());
      if (time.isAfter(startTimeFirst) && time.isBefore(startTimeSecond)) {
        return endTimeFirst;
      }
    }
    return LocalTime.parse(openingHoursList.get(0).getEndTime());
  }

  private DateTime getExpectedDateTimeOfOpeningDay(OpeningDayPeriod openingDayPeriod, LocalTime time,
                                                   String intervalHours, int duration) {
    OpeningDay openingDay = openingDayPeriod.getOpeningDay();
    LocalTime timeShift = intervalHours.equals(INTERVAL_HOURS)
      ? time.plusHours(duration)
      : time.plusMinutes(duration);

    LocalDate date = LocalDate.parse(openingDay.getDate(), DATE_TIME_FORMATTER);
    return timeZoneWrapper(LocalDateTime.of(date, timeShift));
  }

  private DateTime getExpectedDateTimeOfOpeningAllDay(OpeningDayPeriod openingDayPeriod,
                                                      String offsetInterval, int offsetDuration) {
    OpeningDay openingDay = openingDayPeriod.getOpeningDay();
    LocalDate date = LocalDate.parse(openingDay.getDate(), DATE_TIME_FORMATTER);

    LocalTime timeOffset = offsetInterval.equals(INTERVAL_HOURS)
      ? LocalTime.MIN.plusHours(offsetDuration)
      : LocalTime.MIN.plusMinutes(offsetDuration);

    return timeZoneWrapper(LocalDateTime.of(date, timeOffset));
  }

  private DateTime timeZoneWrapper(LocalDateTime dateTime) {
    return new DateTime(dateTime.toString()).withZoneRetainFields(DateTimeZone.UTC);
  }

  private String createLoanPolicy(JsonObject loanPolicyEntry)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {
    IndividualResource resource = loanPolicyClient.create(loanPolicyEntry);
    policiesToDelete.add(resource.getId());
    useLoanPolicyAsFallback(resource.getId());
    return resource.getId().toString();
  }

  /**
   * Create a fake json LoanPolicy
   */
  private JsonObject createLoanPolicyOffsetTimeEntry(int duration, String intervalId, int offsetDuration, String
    offsetInterval) {

    JsonObject period = new JsonObject()
      .put("duration", duration)
      .put("intervalId", intervalId);

    JsonObject openingTimeOffset = new JsonObject()
      .put("duration", offsetDuration)
      .put("intervalId", offsetInterval);

    return new JsonObject()
      .put("name", LOAN_POLICY_NAME)
      .put("description", "Full LoanPolicy")
      .put("loanable", true)
      .put("renewable", true)
      .put("loansPolicy", new JsonObject()
        .put("profileId", POLICY_PROFILE_NAME)
        .put("period", period)
        .put("openingTimeOffset", openingTimeOffset)
        .put("closedLibraryDueDateManagementId",
          DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS.getValue()))
      .put("renewalsPolicy", new JsonObject()
        .put("renewFromId", "CURRENT_DUE_DATE")
        .put("differentPeriod", false));
  }
}
