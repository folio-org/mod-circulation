package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

class RollingDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE =
    "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy.";

  private static final String UNRECOGNISED_INTERVAL_MESSAGE =
    "Item can't be checked out as the interval \"%s\" in the loan policy is not recognised.";

  private static final String INVALID_DURATION_MESSAGE =
    "Item can't be checked out as the duration \"%s\" in the loan policy is invalid.";

  private static final String UNRECOGNISED_PERIOD_MESSAGE =
    "Item can't be checked out as the loan period in the loan policy is not recognised.";

  private final String intervalId;
  private final Integer duration;
  private final FixedDueDateSchedules dueDateLimitSchedules;

  RollingDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    String intervalId,
    Integer duration,
    FixedDueDateSchedules dueDateLimitSchedules) {

    super(loanPolicyId, loanPolicyName);
    this.intervalId = intervalId;
    this.duration = duration;
    this.dueDateLimitSchedules = dueDateLimitSchedules;
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan) {
    final DateTime loanDate = DateTime.parse(loan.getString("loanDate"));

    logApplying(String.format("Rolling %s %s due date calculation", duration, intervalId));

    return calculateRollingDueDate(loanDate, intervalId, duration)
      .next(dueDate -> limitDueDateBySchedule(loanDate, dueDate));
  }

  private HttpResult<DateTime> calculateRollingDueDate(
    DateTime loanDate,
    String interval,
    Integer duration) {

    if(interval == null) {
      return fail(UNRECOGNISED_PERIOD_MESSAGE);
    }

    if(duration == null) {
      return fail(UNRECOGNISED_PERIOD_MESSAGE);
    }

    if(duration <= 0) {
      return fail(String.format(INVALID_DURATION_MESSAGE, duration));
    }

    switch (interval) {
      case "Months":
        return HttpResult.success(loanDate.plusMonths(duration));
      case "Weeks":
        return HttpResult.success(loanDate.plusWeeks(duration));
      case "Days":
        return HttpResult.success(loanDate.plusDays(duration));
      case "Hours":
        return HttpResult.success(loanDate.plusHours(duration));
      case "Minutes":
        return HttpResult.success(loanDate.plusMinutes(duration));
      default:
        return fail(String.format(UNRECOGNISED_INTERVAL_MESSAGE, interval));
    }
  }

  private HttpResult<DateTime> limitDueDateBySchedule(
    DateTime loanDate,
    DateTime dueDate) {

    if(dueDateLimitSchedules != null) {
      return dueDateLimitSchedules.findDueDateFor(loanDate)
        .map(limit -> earliest(dueDate, limit))
        .map(HttpResult::success)
        .orElseGet(() -> fail(NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE));
    }
    else {
      return HttpResult.success(dueDate);
    }
  }

  private DateTime earliest(DateTime rollingDueDate, DateTime limit) {
    return limit.isBefore(rollingDueDate)
      ? limit
      : rollingDueDate;
  }
}
