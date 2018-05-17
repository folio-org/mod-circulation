package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;

import java.util.List;
import java.util.function.Predicate;

class FixedScheduleDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE =
    "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy.";

  private final FixedDueDateSchedules fixedDueDateSchedules;

  FixedScheduleDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules) {

    super(loanPolicyId, loanPolicyName);
    this.fixedDueDateSchedules = fixedDueDateSchedules;
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan) {
    final DateTime loanDate = DateTime.parse(loan.getString("loanDate"));

    logApplying("Fixed schedule due date calculation");

    try {
      final List<JsonObject> schedules = JsonArrayHelper.toList(
        fixedDueDateSchedules.getJsonArray("schedules"));

      if(schedules.isEmpty()) {
        return fail(NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE);
      }
      else {
        return schedules
          .stream()
          .filter(scheduleOverlaps(loanDate))
          .findFirst()
          .map(this::getDueDate)
          .orElseGet(() -> fail(NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE));
      }
    }
    catch(Exception e) {
      logException(e, "Error occured during fixed schedule due date calculation");
      return HttpResult.failure(new ServerErrorFailure(e));
    }
  }

  private Predicate<? super JsonObject> scheduleOverlaps(DateTime loanDate) {
    return schedule -> {
      DateTime from = DateTime.parse(schedule.getString("from"));
      DateTime to = DateTime.parse(schedule.getString("to"));

      return loanDate.isAfter(from) && loanDate.isBefore(to);
    };
  }

  private HttpResult<DateTime> getDueDate(JsonObject schedule) {
    return HttpResult.success(DateTime.parse(
    schedule.getString("due")));
  }
}
