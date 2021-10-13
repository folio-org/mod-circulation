package org.folio.circulation.domain.policy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Predicate;

//TODO: Can this be combined with Period class?
public enum LoanPolicyPeriod {

  MONTHS, WEEKS, DAYS, HOURS, MINUTES, INCORRECT;

  public static LoanPolicyPeriod getProfileByName(String name) {
    return Arrays.stream(values())
      .filter(predicate(name))
      .findFirst()
      .orElse(INCORRECT);
  }

  private static final EnumSet<LoanPolicyPeriod> SHORT_TERM_LOANS = EnumSet.of(HOURS, MINUTES);

  public static boolean isShortTermLoans(LoanPolicyPeriod period) {
    return SHORT_TERM_LOANS.contains(period);
  }

  private static Predicate<LoanPolicyPeriod> predicate(String name) {
    return period -> period.name().equalsIgnoreCase(name);
  }

  public static Duration calculateDuration(LoanPolicyPeriod period, int duration) {
    switch (period) {
      case MONTHS:
        return Duration.of(duration, ChronoUnit.MONTHS);
      case WEEKS:
        return Duration.of(duration, ChronoUnit.WEEKS);
      case DAYS:
        return Duration.of(duration, ChronoUnit.DAYS);
      case HOURS:
        return Duration.of(duration, ChronoUnit.HOURS);
      case MINUTES:
        return Duration.of(duration, ChronoUnit.MINUTES);
      default:
        return Duration.of(duration, ChronoUnit.MILLIS);
    }
  }
}
