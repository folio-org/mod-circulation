package org.folio.circulation.domain.policy.library;

import static java.util.Collections.emptyMap;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.isShortTermLoans;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.ExpirationDateManagement;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;

public final class ClosedLibraryStrategyUtils {

  private ClosedLibraryStrategyUtils() {
  }

  public static ClosedLibraryStrategy determineClosedLibraryStrategy(
    LoanPolicy loanPolicy, ZonedDateTime startDate, ZoneId zone) {
    DueDateManagement dueDateManagement = loanPolicy.getDueDateManagement();
    LoanPolicyPeriod offsetInterval = loanPolicy.getOffsetPeriodInterval();
    int offsetDuration = loanPolicy.getOffsetPeriodDuration();

    switch (dueDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
        return new EndOfPreviousDayStrategy(zone);
      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        return new EndOfNextOpenDayStrategy(zone);
      case MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS:
        return new EndOfCurrentHoursStrategy(startDate, zone);
      case MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS:
        return new BeginningOfNextOpenHoursStrategy(
          offsetInterval, offsetDuration, zone);
      case KEEP_THE_CURRENT_DUE_DATE:
        return new KeepCurrentDateStrategy(zone);
      case KEEP_THE_CURRENT_DUE_DATE_TIME:
      default:
        return new KeepCurrentDateTimeStrategy();
    }
  }

  public static ClosedLibraryStrategy determineClosedLibraryStrategyForHoldShelfExpirationDate(
    ExpirationDateManagement expirationDateManagement, ZonedDateTime requestedDate, ZoneId zone, Duration intervalDuration) {

    switch (expirationDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
        return new EndOfPreviousDayStrategy(zone);
      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        return new EndOfNextOpenDayStrategy(zone);
      case MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS:
        return new EndOfCurrentHoursStrategy(requestedDate, zone);
      case KEEP_THE_CURRENT_DUE_DATE:
        return new KeepCurrentDateStrategy(zone);
      case MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS:
        return new BeginningOfNextOpenHoursStrategy(intervalDuration, zone);
      case KEEP_THE_CURRENT_DUE_DATE_TIME:
      default:
        return new KeepCurrentDateTimeStrategy();
    }
  }

  public static ClosedLibraryStrategy determineStrategyForMovingBackward(
    LoanPolicy loanPolicy, ZonedDateTime startDate, ZoneId zone) {
    LoanPolicyPeriod loanPeriod = loanPolicy.getPeriodInterval();

    return isShortTermLoans(loanPeriod)
      ? new EndOfCurrentHoursStrategy(startDate, zone)
      : new EndOfPreviousDayStrategy(zone);
  }

  //TODO: Should have parameters for validation error
  static ValidationErrorFailure failureForAbsentTimetable() {
    String message = "Calendar timetable is absent for requested date";
    return singleValidationError(new ValidationError(message, emptyMap()));
  }

  public static ClosedLibraryStrategy determineClosedLibraryStrategyForTruncatedDueDate(
    LoanPolicy loanPolicy, ZonedDateTime startDate, ZoneId zone) {
    DueDateManagement dueDateManagement = loanPolicy.getDueDateManagement();

    switch (dueDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        return new EndOfPreviousDayTruncateStrategy(zone);
      case MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS:
      case MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS:
        return new EndOfPreviousOpenHoursTruncateStrategy(startDate, zone);
      case KEEP_THE_CURRENT_DUE_DATE:
      case KEEP_THE_CURRENT_DUE_DATE_TIME:
      default:
        return new KeepCurrentDateTimeStrategy();
    }
  }
}
