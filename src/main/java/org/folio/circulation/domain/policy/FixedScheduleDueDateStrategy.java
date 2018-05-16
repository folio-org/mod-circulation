package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;

import java.util.List;
import java.util.function.Predicate;

class FixedScheduleDueDateStrategy extends DueDateStrategy {
  FixedScheduleDueDateStrategy() {
    super();
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan, LoanPolicy loanPolicy) {
    final DateTime loanDate = DateTime.parse(loan.getString("loanDate"));
    final JsonObject loansPolicy = loanPolicy.getJsonObject("loansPolicy");
    final String profile = loansPolicy.getString("profileId");
    final String loanPolicyId = loanPolicy.getString("id");

    log.info("Applying loan policy {}, profile: {}", loanPolicyId, profile);

    try {
      final List<JsonObject> schedules = JsonArrayHelper.toList(
        loanPolicy.fixedDueDateSchedules.getJsonArray("schedules"));

      if(schedules.isEmpty()) {
        return fail("No schedules for fixed due date loan policy", loanPolicyId);
      }
      else {
        return schedules
          .stream()
          .filter(scheduleOverlaps(loanDate))
          .findFirst()
          .map(this::getDueDate)
          .orElseGet(() -> fail("Loan date is not within a schedule", loanPolicyId));
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
