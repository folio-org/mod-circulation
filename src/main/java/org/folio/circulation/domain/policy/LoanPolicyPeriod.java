package org.folio.circulation.domain.policy;

import org.joda.time.Period;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Predicate;

public enum LoanPolicyPeriod {

  MONTHS, WEEKS, DAYS, HOURS, MINUTES, INCORRECT;

  public static LoanPolicyPeriod getProfileByName(String name) {
    return Arrays.stream(values())
      .filter(predicate(name))
      .findFirst()
      .orElse(INCORRECT);
  }

  private static final EnumSet<LoanPolicyPeriod> LONG_TERM_LOANS = EnumSet.of(MONTHS, WEEKS, DAYS);
  private static final EnumSet<LoanPolicyPeriod> SHORT_TERM_LOANS = EnumSet.of(HOURS, MINUTES);

  public static boolean isLongTermLoans(LoanPolicyPeriod period) {
    return LONG_TERM_LOANS.contains(period);
  }

  public static boolean isShortTermLoans(LoanPolicyPeriod period) {
    return SHORT_TERM_LOANS.contains(period);
  }

  private static Predicate<LoanPolicyPeriod> predicate(String name) {
    return period -> period.name().equalsIgnoreCase(name);
  }

  public static Period calculatePeriod(LoanPolicyPeriod period, int duration) {
    switch (period) {
      case MONTHS:
        return Period.months(duration);
      case WEEKS:
        return Period.weeks(duration);
      case DAYS:
        return Period.days(duration);
      case HOURS:
        return Period.hours(duration);
      case MINUTES:
        return Period.minutes(duration);
      default:
        return Period.millis(duration);
    }
  }
}
