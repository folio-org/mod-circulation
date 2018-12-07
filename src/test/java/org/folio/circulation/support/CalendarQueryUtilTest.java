package org.folio.circulation.support;

import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.folio.circulation.domain.policy.DueDateManagement.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

public class CalendarQueryUtilTest {

  private static final String PATH_TEMPLATE = "startDate=%s&unit=%s&amount=%s";

  private List<DueDateManagement> dueDateList = new ArrayList<>(
    EnumSet.of(KEEP_THE_CURRENT_DUE_DATE,
      MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY,
      MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY,
      MOVE_TO_THE_END_OF_THE_CURRENT_DAY,
      KEEP_THE_CURRENT_DUE_DATE_TIME,
      MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS));

  @Test
  public void testPathQuery() {
    LocalDate now = LocalDate.now(ZoneOffset.UTC);
    String currentDate = now.toString();

    assertPathQuery(0, LoanPolicyPeriod.DAYS, currentDate, "day", 0);
    assertPathQuery(1, LoanPolicyPeriod.DAYS, currentDate, "day", 1);
    assertPathQuery(31, LoanPolicyPeriod.DAYS, currentDate, "day", 31);

    assertPathQuery(1, LoanPolicyPeriod.MONTHS, currentDate, "day",
      (int) DAYS.between(now, now.plusMonths(1)));
    assertPathQuery(12, LoanPolicyPeriod.MONTHS, currentDate, "day",
      (int) DAYS.between(now, now.plusMonths(12)));
    assertPathQuery(4, LoanPolicyPeriod.MONTHS, currentDate, "day",
      (int) DAYS.between(now, now.plusMonths(4)));

    assertPathQuery(1, LoanPolicyPeriod.WEEKS, currentDate, "day",
      (int) DAYS.between(now, now.plusWeeks(1)));
    assertPathQuery(10, LoanPolicyPeriod.WEEKS, currentDate, "day",
      (int) DAYS.between(now, now.plusWeeks(10)));

    assertPathQuery(1, LoanPolicyPeriod.HOURS, currentDate, "hour", 1);
    assertPathQuery(24, LoanPolicyPeriod.HOURS, currentDate, "hour", 24);
    assertPathQuery(48, LoanPolicyPeriod.HOURS, currentDate, "hour", 48);

    assertPathQuery(1, LoanPolicyPeriod.MINUTES, currentDate, "hour", 1);
    assertPathQuery(30, LoanPolicyPeriod.MINUTES, currentDate, "hour", 1);
    assertPathQuery(60, LoanPolicyPeriod.MINUTES, currentDate, "hour", 1);
    assertPathQuery(120, LoanPolicyPeriod.MINUTES, currentDate, "hour", 2);
  }

  @Test
  public void testPathQueryOffsetTime() {
    LocalDate now = LocalDate.now(ZoneOffset.UTC);
    String currentDate = now.toString();

    assertPathQueryOffsetTime(1, LoanPolicyPeriod.HOURS, 1, LoanPolicyPeriod.HOURS,
      currentDate, 2);
    assertPathQueryOffsetTime(12, LoanPolicyPeriod.HOURS, 12, LoanPolicyPeriod.HOURS,
      currentDate, 24);
    assertPathQueryOffsetTime(24, LoanPolicyPeriod.HOURS, 24, LoanPolicyPeriod.HOURS,
      currentDate, 48);

    assertPathQueryOffsetTime(1, LoanPolicyPeriod.HOURS, 1, LoanPolicyPeriod.MINUTES,
      currentDate, 1);
    assertPathQueryOffsetTime(1, LoanPolicyPeriod.HOURS, 60, LoanPolicyPeriod.MINUTES,
      currentDate, 2);
    assertPathQueryOffsetTime(22, LoanPolicyPeriod.HOURS, 120, LoanPolicyPeriod.MINUTES,
      currentDate, 24);

    assertPathQueryOffsetTime(1, LoanPolicyPeriod.MINUTES, 1, LoanPolicyPeriod.MINUTES,
      currentDate, 1);
    assertPathQueryOffsetTime(30, LoanPolicyPeriod.MINUTES, 30, LoanPolicyPeriod.MINUTES,
      currentDate, 1);
    assertPathQueryOffsetTime(60, LoanPolicyPeriod.MINUTES, 30, LoanPolicyPeriod.MINUTES,
      currentDate, 1);
    assertPathQueryOffsetTime(60, LoanPolicyPeriod.MINUTES, 60, LoanPolicyPeriod.MINUTES,
      currentDate, 2);
    assertPathQueryOffsetTime(45, LoanPolicyPeriod.MINUTES, 75, LoanPolicyPeriod.MINUTES,
      currentDate, 2);

    assertPathQueryOffsetTime(1, LoanPolicyPeriod.MINUTES, 1, LoanPolicyPeriod.HOURS,
      currentDate, 1);
    assertPathQueryOffsetTime(60, LoanPolicyPeriod.MINUTES, 1, LoanPolicyPeriod.HOURS,
      currentDate, 2);
    assertPathQueryOffsetTime(60, LoanPolicyPeriod.MINUTES, 23, LoanPolicyPeriod.HOURS,
      currentDate, 24);

  }

  private void assertPathQueryOffsetTime(int duration, LoanPolicyPeriod period,
                                         int offsetDuration, LoanPolicyPeriod offsetPeriodInterval,
                                         String expectedDate, int expectedAmount) {

    String actualPathQuery = getPathQueryOffsetVal(duration, period, offsetDuration, offsetPeriodInterval);
    String expectedPathQuery = String.format(PATH_TEMPLATE, expectedDate, "hour", expectedAmount);

    assertThat(actualPathQuery, containsString(expectedPathQuery));
  }

  private void assertPathQuery(int duration, LoanPolicyPeriod period,
                               String expectedDate, String expectedUnit, int expectedAmount) {

    String actualPathQuery = getPathQuery(duration, period);
    String expectedPathQuery = String.format(PATH_TEMPLATE, expectedDate, expectedUnit, expectedAmount);

    assertThat(actualPathQuery, containsString(expectedPathQuery));
  }

  private String getPathQueryOffsetVal(int duration, LoanPolicyPeriod period, int offsetDuration, LoanPolicyPeriod offsetPeriodInterval) {
    return CalendarQueryUtil.collectPathQuery(UUID.randomUUID().toString(),
      duration, period, DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
      offsetDuration, offsetPeriodInterval);
  }

  private String getPathQuery(int duration, LoanPolicyPeriod period) {
    return CalendarQueryUtil.collectPathQuery(UUID.randomUUID().toString(),
      duration, period, getRandomDueDateManagement(),
      0, LoanPolicyPeriod.INCORRECT);
  }

  private DueDateManagement getRandomDueDateManagement() {
    int position = new SplittableRandom().nextInt(1, dueDateList.size() - 1);
    return dueDateList.get(position);
  }
}
