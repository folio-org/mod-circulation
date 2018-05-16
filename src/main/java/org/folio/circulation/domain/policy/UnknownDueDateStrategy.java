package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

class UnknownDueDateStrategy extends DueDateStrategy {
  private String profileId;

  UnknownDueDateStrategy(String loanPolicyId, String profileId) {
    super(loanPolicyId);
    this.profileId = profileId;
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan, LoanPolicy loanPolicy) {
    return fail(String.format("Unrecognised profile - %s", profileId));
  }
}
