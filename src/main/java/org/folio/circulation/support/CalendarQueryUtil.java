package org.folio.circulation.support;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;

import java.util.List;
import java.util.Optional;

public class CalendarQueryUtil {

  private static final String PATH_PARAM_WITH_QUERY = "%s/calculateopening?startDate=%s&unit=%s&amount=%s";

  private static final String DAY_QUERY_VAL = "day";
  private static final String HOUR_QUERY_VAL = "hour";
  private static final int DEFAULT_AMOUNT = 1;
  private static final int FULL_HOUR = 60;

  private CalendarQueryUtil() {
    // not use
  }

  public static String getField(String val) {
    return StringUtils.isBlank(val) ? StringUtils.EMPTY : val;
  }

  public static String collectPathQueryForFixedSchedules(String servicePointId, List<DateTime> fixedDueDates) {
    DateTime nowDateTime = DateTime.now().millisOfDay().withMaximumValue().withZone(DateTimeZone.UTC);
    String startDate = nowDateTime.toLocalDate().toString();
    int amount = 0;

    Optional<DateTime> fixedDueDateOpt = fixedDueDates.stream()
      .filter(fixedDueDate -> fixedDueDate.isAfter(nowDateTime))
      .findFirst();

    if (fixedDueDateOpt.isPresent()) {
      DateTime fixedDueDate = fixedDueDateOpt.get();
      amount = Days.daysBetween(nowDateTime, fixedDueDate).getDays();
    }

    return String.format(PATH_PARAM_WITH_QUERY, servicePointId, startDate, DAY_QUERY_VAL, amount);
  }

  public static String collectPathQuery(String servicePointId,
                                        int duration, LoanPolicyPeriod interval) {
    String unit;
    int amount;
    DateTime nowDateTime = DateTime.now().withZone(DateTimeZone.UTC);
    String startDate = nowDateTime.toLocalDate().toString();

    switch (interval) {
      case MONTHS:
        unit = DAY_QUERY_VAL;
        DateTime shiftMonthDate = nowDateTime.plusMonths(duration);
        amount = Days.daysBetween(nowDateTime, shiftMonthDate).getDays();
        break;

      case WEEKS:
        unit = DAY_QUERY_VAL;
        DateTime shiftWeekDate = nowDateTime.plusWeeks(duration);
        amount = Days.daysBetween(nowDateTime, shiftWeekDate).getDays();
        break;

      case DAYS:
        unit = DAY_QUERY_VAL;
        amount = duration;
        break;

      case HOURS:
        amount = duration;
        unit = HOUR_QUERY_VAL;
        break;

      case MINUTES:
        amount = calculateAmountForMinutes(duration);
        unit = HOUR_QUERY_VAL;
        break;

      default:
        unit = HOUR_QUERY_VAL;
        amount = DEFAULT_AMOUNT;
    }

    return String.format(PATH_PARAM_WITH_QUERY, servicePointId, startDate, unit, amount);
  }

  private static int calculateAmountForMinutes(int duration) {
    int fullHour = duration / FULL_HOUR;
    return (fullHour == 0) ? DEFAULT_AMOUNT : fullHour;
  }
}
