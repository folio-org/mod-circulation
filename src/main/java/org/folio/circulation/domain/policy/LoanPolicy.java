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

public class LoanPolicy extends JsonObject {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final JsonObject fixedDueDateSchedules;

  private LoanPolicy(JsonObject representation) {
    this(representation, null);
  }

  LoanPolicy(JsonObject representation, JsonObject fixedDueDateSchedules) {
    super(representation.getMap());

    this.fixedDueDateSchedules = fixedDueDateSchedules;
  }

  static LoanPolicy from(JsonObject representation) {
    return new LoanPolicy(representation);
  }

  private HttpResult<DateTime> calculateRollingDueDate(
    DateTime loanDate,
    String interval,
    Integer duration,
    String loanPolicyId) {

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
      return fail(String.format("Unrecognised interval - %s", interval), loanPolicyId);
    }
  }

  private HttpResult<DateTime> determineFixedDueDate(
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
          .map(this::getDueDate)
          .orElseGet(() -> fail("Loan date is not within a schedule", loanPolicyId));
      }
    }
    catch(Exception e) {
      log.error("Error occurred during fixed due date determination", e);
      return HttpResult.failure(new ServerErrorFailure(e));
    }
  }

  private HttpResult<DateTime> getDueDate(JsonObject schedule) {
    return HttpResult.success(DateTime.parse(
    schedule.getString("due")));
  }

  private Predicate<? super JsonObject> scheduleOverlaps(DateTime loanDate) {
    return schedule -> {
      DateTime from = DateTime.parse(schedule.getString("from"));
      DateTime to = DateTime.parse(schedule.getString("to"));

      return loanDate.isAfter(from) && loanDate.isBefore(to);
    };
  }

  private HttpResult<DateTime> fail(String reason, String loanPolicyId) {
    final String message = String.format(
      "Loans policy cannot be applied - %s", reason);

    log.warn(message);

    return HttpResult.failure(new ValidationErrorFailure(
      message, "loanPolicyId", loanPolicyId));
  }

  public HttpResult<DateTime> calculate(JsonObject loan) {
    final DateTime loanDate = DateTime.parse(
      loan.getString("loanDate"));

    final JsonObject loansPolicy = getJsonObject("loansPolicy");

    final String profile = loansPolicy.getString("profileId");

    final String loanPolicyId = getString("id");

    if(profile.equalsIgnoreCase("Rolling")) {
      final JsonObject period = loansPolicy.getJsonObject("period");

      final String interval = period.getString("intervalId");
      final Integer duration = period.getInteger("duration");

      log.info("Applying loan policy {}, profile: {}, period: {} {}",
        loanPolicyId, profile, duration, interval);

      return calculateRollingDueDate(loanDate, interval, duration, loanPolicyId);
    }
    if(profile.equalsIgnoreCase("Fixed")) {
      log.info("Applying loan policy {}, profile: {}", loanPolicyId, profile);

      return determineFixedDueDate(loanDate,
        this.fixedDueDateSchedules, loanPolicyId);
    }
    else {
      return fail(String.format("Unrecognised profile - %s", profile), loanPolicyId);
    }
  }

  LoanPolicy withDueDateSchedule(JsonObject fixedDueDateSchedules) {
    return new LoanPolicy(this, fixedDueDateSchedules);
  }
}
