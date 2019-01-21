package org.folio.circulation.support;

import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

public class CalendarQueryUtilTest {

  private static final String PATH_TEMPLATE = "startDate=%s&unit=%s&amount=%s";
  
  /**
   * The `mod-calendar` API of the module according to the specification takes the values `day` and `hour`
   * for the path parameter `unit`
   */
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

    assertPathQuery(0, LoanPolicyPeriod.HOURS, currentDate, "hour", 0);
    assertPathQuery(1, LoanPolicyPeriod.HOURS, currentDate, "hour", 1);
    assertPathQuery(24, LoanPolicyPeriod.HOURS, currentDate, "hour", 24);
    assertPathQuery(36, LoanPolicyPeriod.HOURS, currentDate, "hour", 36);
    assertPathQuery(48, LoanPolicyPeriod.HOURS, currentDate, "hour", 48);

    assertPathQuery(0, LoanPolicyPeriod.MINUTES, currentDate, "hour", 1);
    assertPathQuery(1, LoanPolicyPeriod.MINUTES, currentDate, "hour", 1);
    assertPathQuery(30, LoanPolicyPeriod.MINUTES, currentDate, "hour", 1);
    assertPathQuery(60, LoanPolicyPeriod.MINUTES, currentDate, "hour", 1);
    assertPathQuery(120, LoanPolicyPeriod.MINUTES, currentDate, "hour", 2);
  }

  private void assertPathQuery(int duration, LoanPolicyPeriod period,
                               String expectedDate, String expectedUnit, int expectedAmount) {

    String actualPathQuery = getPathQuery(duration, period);
    String expectedPathQuery = String.format(PATH_TEMPLATE, expectedDate, expectedUnit, expectedAmount);

    assertThat(actualPathQuery, containsString(expectedPathQuery));
  }

  private String getPathQuery(int duration, LoanPolicyPeriod period) {
    return CalendarQueryUtil.collectPathQuery(UUID.randomUUID().toString(),
      duration, period);
  }
}
