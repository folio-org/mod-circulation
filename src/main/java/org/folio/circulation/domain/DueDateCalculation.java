package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class DueDateCalculation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public HttpResult<DateTime> calculate(JsonObject loan, LoanPolicy loanPolicy) {
    final DateTime loanDate = DateTime.parse(
      loan.getString("loanDate"));

    final JsonObject loansPolicy = loanPolicy.getJsonObject("loansPolicy");

    final String profile = loansPolicy.getString("profileId");

    final String loanPolicyId = loanPolicy.getString("id");

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

      return determineFixedDueDate(loanDate, loanPolicy.fixedDueDateSchedules, loanPolicyId);
    }
    else {
      return fail("Unrecognised profile", profile, loanPolicyId);
    }
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
      return fail("Unrecognised interval", interval, loanPolicyId);
    }
  }

  private HttpResult<DateTime> determineFixedDueDate(
    DateTime loanDate,
    JsonObject fixedDueDateSchedules,
    String loanPolicyId) {

    try {
      return HttpResult.success(DateTime.parse(
        JsonArrayHelper.toList(fixedDueDateSchedules.getJsonArray("schedules"))
          .stream()
          .findFirst()
          .get()
          .getString("due")));
    }
    catch(Exception e) {
      return HttpResult.failure(new ServerErrorFailure(e));
    }
  }

  private HttpResult<DateTime> fail(
    String reason,
    String parameterValue,
    String loanPolicyId) {

    final String message = String.format(
      "Loans policy cannot be applied - %s: %s", reason, parameterValue);

    log.error(message);

    return HttpResult.failure(new ValidationErrorFailure(
      message,
      "loanPolicyId", loanPolicyId));
  }
}
