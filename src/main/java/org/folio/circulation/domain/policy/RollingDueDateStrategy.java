package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

class RollingDueDateStrategy extends DueDateStrategy {
  private final String intervalId;
  private final Integer duration;

  RollingDueDateStrategy(String loanPolicyId, String intervalId, Integer duration) {
    super(loanPolicyId);
    this.intervalId = intervalId;
    this.duration = duration;
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan) {
    final DateTime loanDate = DateTime.parse(loan.getString("loanDate"));

    log.info("Applying rolling due date loan policy {}, period: {} {}",
      loanPolicyId, duration, intervalId);

    return calculateRollingDueDate(loanDate, intervalId, duration);
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
}
