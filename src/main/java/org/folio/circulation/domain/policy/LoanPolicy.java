package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

public class LoanPolicy extends JsonObject {
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

  public HttpResult<DateTime> calculate(JsonObject loan) {
    return determineStrategy().calculate(loan);
  }

  private DueDateStrategy determineStrategy() {
    final String loanPolicyId = getString("id");
    final String loanPolicyName = getString("name");

    final JsonObject loansPolicy = getJsonObject("loansPolicy");
    final String profileId = loansPolicy.getString("profileId");

    if(profileId.equalsIgnoreCase("Rolling")) {
      final JsonObject period = loansPolicy.getJsonObject("period");

      final String interval = period.getString("intervalId");
      final Integer duration = period.getInteger("duration");

      return new RollingDueDateStrategy(loanPolicyId, loanPolicyName,
        interval, duration, fixedDueDateSchedules);
    }
    else if(profileId.equalsIgnoreCase("Fixed")) {
      return new FixedScheduleDueDateStrategy(loanPolicyId,
        fixedDueDateSchedules);
    }
    else {
      return new UnknownDueDateStrategy(loanPolicyId, profileId);
    }
  }

  LoanPolicy withDueDateSchedules(JsonObject fixedDueDateSchedules) {
    return new LoanPolicy(this, fixedDueDateSchedules);
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
