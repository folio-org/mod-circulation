package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;

import java.util.List;
import java.util.function.Predicate;

class FixedScheduleDueDateStrategy extends DueDateStrategy {
  FixedScheduleDueDateStrategy(String loanPolicyId) {
    super(loanPolicyId);
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan, LoanPolicy loanPolicy) {
    final DateTime loanDate = DateTime.parse(loan.getString("loanDate"));

    log.info("Applying fixed due date schedule loan policy {}", loanPolicyId);

    try {
      final List<JsonObject> schedules = JsonArrayHelper.toList(
        loanPolicy.fixedDueDateSchedules.getJsonArray("schedules"));

      if(schedules.isEmpty()) {
        return fail("No schedules for fixed due date loan policy");
      }
      else {
        return schedules
          .stream()
          .filter(scheduleOverlaps(loanDate))
          .findFirst()
          .map(this::getDueDate)
          .orElseGet(() -> fail("Loan date is not within a schedule"));
      }
    }
    catch(Exception e) {
      log.error("Error occurred during fixed due date determination", e);
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
