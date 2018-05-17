package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.joda.time.DateTime;

import java.util.List;
import java.util.function.Predicate;

class RollingDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE =
    "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy.";

  private static final String UNRECOGNISED_INTERVAL_MESSAGE =
    "Item can't be checked out as the interval \"%s\" in the loan policy is not recognised.";

  private final String intervalId;
  private final Integer duration;
  private final JsonObject dueDateLimitSchedules;

  RollingDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    String intervalId,
    Integer duration,
    JsonObject dueDateLimitSchedules) {

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

    if(interval.equals("Months") && duration != null) {
      return HttpResult.success(loanDate.plusMonths(duration));
    }
    else if(interval.equals("Weeks") && duration != null) {
      return HttpResult.success(loanDate.plusWeeks(duration));
    }
    else if(interval.equals("Days") && duration != null) {
      return HttpResult.success(loanDate.plusDays(duration));
    }
    else if(interval.equals("Hours") && duration != null) {
      return HttpResult.success(loanDate.plusHours(duration));
    }
    else if(interval.equals("Minutes") && duration != null) {
      return HttpResult.success(loanDate.plusMinutes(duration));
    }
    else {
      return fail(String.format(UNRECOGNISED_INTERVAL_MESSAGE, interval));
    }
  }

  private HttpResult<DateTime> limitDueDateBySchedule(
    DateTime loanDate,
    DateTime dueDate) {

    if(dueDateLimitSchedules != null) {
      final List<JsonObject> schedules = JsonArrayHelper.toList(
        dueDateLimitSchedules.getJsonArray("schedules"));

      return schedules
        .stream()
        .filter(scheduleOverlaps(loanDate))
        .findFirst()
        .map(this::getDueDate)
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

  private Predicate<? super JsonObject> scheduleOverlaps(DateTime loanDate) {
    return schedule -> {
      DateTime from = DateTime.parse(schedule.getString("from"));
      DateTime to = DateTime.parse(schedule.getString("to"));

      return loanDate.isAfter(from) && loanDate.isBefore(to);
    };
  }

  private DateTime getDueDate(JsonObject schedule) {
    return DateTime.parse(schedule.getString("due"));
  }
}
