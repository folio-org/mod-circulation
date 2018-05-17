package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

class UnknownDueDateStrategy extends DueDateStrategy {
  private static final String UNRECOGNISED_PROFILE_MESSAGE =
    "Item can't be checked out as profile \"%s\" in the loan policy is not recognised.";

  private String profileId;

  UnknownDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    String profileId) {

    super(loanPolicyId, loanPolicyName);
    this.profileId = profileId;
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan) {
    return fail(String.format(UNRECOGNISED_PROFILE_MESSAGE, profileId));
  }
}
