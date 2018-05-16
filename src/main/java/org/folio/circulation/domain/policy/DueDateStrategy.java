package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.function.Predicate;

class DueDateStrategy {
  static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  DueDateStrategy() {
  }

  static DueDateStrategy from(LoanPolicy loanPolicy) {
    final JsonObject loansPolicy = loanPolicy.getJsonObject("loansPolicy");
    final String profile = loansPolicy.getString("profileId");

    if(profile.equalsIgnoreCase("Rolling")) {
      return new RollingDueDateStrategy();
    }
    else if(profile.equalsIgnoreCase("Fixed")) {
      return new FixedDueDateStrategy();
    }
    else {
      return new UnknownDueDateStrategy();
    }
  }

  HttpResult<DateTime> calculate(JsonObject loan, LoanPolicy loanPolicy) {
    final DateTime loanDate = DateTime.parse(loan.getString("loanDate"));
    final JsonObject loansPolicy = loanPolicy.getJsonObject("loansPolicy");
    final String profile = loansPolicy.getString("profileId");
    final String loanPolicyId = loanPolicy.getString("id");

    if(profile.equalsIgnoreCase("Fixed")) {
      log.info("Applying loan policy {}, profile: {}", loanPolicyId, profile);

      return determineFixedDueDate(loanDate,
        loanPolicy.fixedDueDateSchedules, loanPolicyId);
    }
    else {
      return fail(String.format("Unrecognised profile - %s", profile), loanPolicyId);
    }
  }

  private static HttpResult<DateTime> determineFixedDueDate(
    DateTime loanDate,
    JsonObject fixedDueDateSchedules,
    String loanPolicyId) {

    try {
      final List<JsonObject> schedules = JsonArrayHelper.toList(
        fixedDueDateSchedules.getJsonArray("schedules"));

      if(schedules.isEmpty()) {
        return fail("No schedules for fixed due date loan policy", loanPolicyId);
      }
      else {
        return schedules
          .stream()
          .filter(scheduleOverlaps(loanDate))
          .findFirst()
          .map(DueDateStrategy::getDueDate)
          .orElseGet(() -> fail("Loan date is not within a schedule", loanPolicyId));
      }
    }
    catch(Exception e) {
      log.error("Error occurred during fixed due date determination", e);
      return HttpResult.failure(new ServerErrorFailure(e));
    }
  }

  private static Predicate<? super JsonObject> scheduleOverlaps(DateTime loanDate) {
    return schedule -> {
      DateTime from = DateTime.parse(schedule.getString("from"));
      DateTime to = DateTime.parse(schedule.getString("to"));

      return loanDate.isAfter(from) && loanDate.isBefore(to);
    };
  }

  private static HttpResult<DateTime> getDueDate(JsonObject schedule) {
    return HttpResult.success(DateTime.parse(
    schedule.getString("due")));
  }

  static HttpResult<DateTime> fail(String reason, String loanPolicyId) {
    final String message = String.format(
      "Loans policy cannot be applied - %s", reason);

    log.warn(message);

    return HttpResult.failure(new ValidationErrorFailure(
      message, "loanPolicyId", loanPolicyId));
  }
}
