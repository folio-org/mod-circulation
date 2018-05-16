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
  protected String loanPolicyId;

  DueDateStrategy(String loanPolicyId) {
    this.loanPolicyId = loanPolicyId;
  }

  static DueDateStrategy from(LoanPolicy loanPolicy) {
    final String loanPolicyId = loanPolicy.getString("id");

    final JsonObject loansPolicy = loanPolicy.getJsonObject("loansPolicy");
    final String profileId = loansPolicy.getString("profileId");

    if(profileId.equalsIgnoreCase("Rolling")) {
      return new RollingDueDateStrategy(loanPolicyId);
    }
    else if(profileId.equalsIgnoreCase("Fixed")) {
      return new FixedScheduleDueDateStrategy(loanPolicyId);
    }
    else {
      return new UnknownDueDateStrategy(loanPolicyId, profileId);
    }
  }

  abstract HttpResult<DateTime> calculate(JsonObject loan, LoanPolicy loanPolicy);

  HttpResult<DateTime> fail(String reason) {
    final String message = String.format(
      "Loans policy cannot be applied - %s", reason);

    log.warn(message);

    return HttpResult.failure(new ValidationErrorFailure(
      message, "loanPolicyId", this.loanPolicyId));
  }
}
