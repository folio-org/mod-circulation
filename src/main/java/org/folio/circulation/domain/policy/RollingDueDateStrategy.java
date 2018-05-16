package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import java.util.List;
import java.util.function.Predicate;

class RollingDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE =
    "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy. Please review %s before retrying checking out";

  private final String intervalId;
  private final Integer duration;
  private final JsonObject dueDateLimitSchedules;
  private final String loanPolicyName;

  RollingDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName, String intervalId,
    Integer duration,
    JsonObject dueDateLimitSchedules) {

    super(loanPolicyId);
    this.loanPolicyName = loanPolicyName;
    this.intervalId = intervalId;
    this.duration = duration;
    this.dueDateLimitSchedules = dueDateLimitSchedules;
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan) {
    final DateTime loanDate = DateTime.parse(loan.getString("loanDate"));

    log.info("Applying rolling due date loan policy {}, period: {} {}",
      loanPolicyId, duration, intervalId);

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
      return fail(String.format("Unrecognised interval - %s", interval));
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
        .orElseGet(() -> {
          final String message = String.format(NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE,
            loanPolicyName);

          log.warn(message);

          return HttpResult.failure(new ValidationErrorFailure(
            message, "loanPolicyId", this.loanPolicyId));
        });
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
