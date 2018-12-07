package org.folio.circulation.domain.policy;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Value from the `ui-circulation` module
 */
public enum DueDateManagement {

  /**
   * Short-term loans:
   * Loan period = Months|Weeks|Days
   * value="CURRENT_DUE_DATE", Keep the current due date</option>
   * value="END_OF_THE_PREVIOUS_OPEN_DAY", Move to the end of the previous open day
   * value="END_OF_THE_NEXT_OPEN_DAY", Move to the end of the next open day
   * value="END_OF_THE_CURRENT_DAY", Move to the end of the current day
   */
  KEEP_THE_CURRENT_DUE_DATE("CURRENT_DUE_DATE"),
  MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY("END_OF_THE_PREVIOUS_OPEN_DAY"),
  MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY("END_OF_THE_NEXT_OPEN_DAY"),
  MOVE_TO_THE_END_OF_THE_CURRENT_DAY("END_OF_THE_CURRENT_DAY"),

  /**
   * Long-term loans::
   * Loan period = Hours|Minutes
   * value="CURRENT_DUE_DATE_TIME", Keep the current due date/time
   * value="END_OF_THE_CURRENT_SERVICE_POINT_HOURS", Move to the end of the current service point hours
   * value="BEGINNING_OF_THE_NEXT_OPEN_SERVICE_POINT_HOURS", Move to the beginning of the next open service point hours
   */
  KEEP_THE_CURRENT_DUE_DATE_TIME("CURRENT_DUE_DATE_TIME"),
  MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS("END_OF_THE_CURRENT_SERVICE_POINT_HOURS"),
  MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS("BEGINNING_OF_THE_NEXT_OPEN_SERVICE_POINT_HOURS");

  String value;

  DueDateManagement(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static DueDateManagement getDueDateManagement(String value) {
    return Arrays.stream(values())
      .filter(predicate(value))
      .findFirst()
      .orElse(KEEP_THE_CURRENT_DUE_DATE);
  }

  private static Predicate<DueDateManagement> predicate(String value) {
    return en -> en.value.equals(value);
  }
}
