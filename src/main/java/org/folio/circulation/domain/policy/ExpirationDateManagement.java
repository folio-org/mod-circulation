package org.folio.circulation.domain.policy;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Value for hold shelf expiration date from service point
 */
public enum ExpirationDateManagement {

  /*
   * Long-term loans:
   * Loan period = Months|Weeks|Days
   */
  KEEP_THE_CURRENT_DUE_DATE,
  MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY,
  MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY,

  /*
   * Short-term loans::
   * Loan period = Hours|Minutes
   */
  KEEP_THE_CURRENT_DUE_DATE_TIME,
  MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS,
  MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;

  final String value;

  ExpirationDateManagement() {
    this.value = name();
  }

  public static ExpirationDateManagement getExpirationDateManagement(String value) {
    return Arrays.stream(values())
      .filter(predicate(value))
      .findFirst()
      .orElse(KEEP_THE_CURRENT_DUE_DATE);
  }

  private static Predicate<ExpirationDateManagement> predicate(String value) {
    return en -> en.value.equalsIgnoreCase(value);
  }
}
