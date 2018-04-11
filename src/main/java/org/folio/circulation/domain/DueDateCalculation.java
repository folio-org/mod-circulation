package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class DueDateCalculation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public HttpResult<DateTime> calculate(JsonObject loan, JsonObject loanPolicy) {
    final DateTime loanDate = DateTime.parse(
      loan.getString("loanDate"));

    final JsonObject loansPolicy = loanPolicy.getJsonObject("loansPolicy");

    final String profile = loansPolicy.getString("profileId");
    final JsonObject period = loansPolicy.getJsonObject("period");

    final String interval = period.getString("intervalId");
    final Integer duration = period.getInteger("duration");

    log.info("Applying loan policy, profile: {}, period: {} {}",
      profile, duration, interval);

    if(profile.equals("Rolling")) {
      if(interval.equals("Weeks") && duration != null) {
        return HttpResult.success(loanDate.plusWeeks(duration));
      }
      else if(interval.equals("Days") && duration != null) {
        return HttpResult.success(loanDate.plusDays(duration));
      }
      else {
        return fail("Unrecognised interval", interval, loanPolicy);
      }
    }
    else {
      return fail("Unrecognised profile", profile, loanPolicy);
    }
  }

  private HttpResult<DateTime> fail(
    String reason,
    String value,
    JsonObject loanPolicy) {

    final String message = String.format(
      "Loans policy cannot be applied - %s: %s", reason, value);

    log.error(message);

    return HttpResult.failure(new ValidationErrorFailure(
      message,
      "loanPolicyId", loanPolicy.getString("id")));
  }
}
