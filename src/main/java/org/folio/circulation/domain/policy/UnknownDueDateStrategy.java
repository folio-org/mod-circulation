package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

class UnknownDueDateStrategy extends DueDateStrategy {
  UnknownDueDateStrategy() {
    super();
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan, LoanPolicy loanPolicy) {
    final JsonObject loansPolicy = loanPolicy.getJsonObject("loansPolicy");
    final String profile = loansPolicy.getString("profileId");
    final String loanPolicyId = loanPolicy.getString("id");

    return fail(String.format("Unrecognised profile - %s", profile), loanPolicyId);
  }
}
