package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
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

    final DateTime dueDate;

    log.info("Applying loan policy, profile: {}, period: {} {}",
      profile, duration, interval);

    if(profile.equals("Rolling") && interval.equals("Weeks") && duration != null) {
      dueDate = loanDate.plusWeeks(duration);
    }
    else {
      log.warn("Defaulting due date to 14 days after loan date");
      dueDate = loanDate.plusDays(14);
    }
    return HttpResult.success(dueDate);
  }
}
