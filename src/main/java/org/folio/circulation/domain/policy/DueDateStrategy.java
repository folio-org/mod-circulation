package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

abstract class DueDateStrategy {
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
      return new FixedScheduleDueDateStrategy();
    }
    else {
      return new UnknownDueDateStrategy();
    }
  }

  abstract HttpResult<DateTime> calculate(JsonObject loan, LoanPolicy loanPolicy);

  HttpResult<DateTime> fail(String reason, String loanPolicyId) {
    final String message = String.format(
      "Loans policy cannot be applied - %s", reason);

    log.warn(message);

    return HttpResult.failure(new ValidationErrorFailure(
      message, "loanPolicyId", loanPolicyId));
  }
}
