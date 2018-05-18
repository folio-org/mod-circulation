package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

import static org.folio.circulation.support.DefensiveJsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.DefensiveJsonPropertyFetcher.getNestedStringProperty;

public class LoanPolicy extends JsonObject {
  private final FixedDueDateSchedules fixedDueDateSchedules;

  private LoanPolicy(JsonObject representation) {
    this(representation, null);
  }

  LoanPolicy(
    JsonObject representation,
    FixedDueDateSchedules fixedDueDateSchedules) {

    super(representation.getMap());
    this. fixedDueDateSchedules = fixedDueDateSchedules;
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
      final String interval = getNestedStringProperty(loansPolicy, "period", "intervalId");
      final Integer duration = getNestedIntegerProperty(loansPolicy, "period", "duration");

      return new RollingDueDateStrategy(loanPolicyId, loanPolicyName,
        interval, duration, fixedDueDateSchedules);
    }
    else if(profileId.equalsIgnoreCase("Fixed")) {
      return new FixedScheduleDueDateStrategy(loanPolicyId, loanPolicyName,
        fixedDueDateSchedules);
    }
    else {
      return new UnknownDueDateStrategy(loanPolicyId, loanPolicyName, profileId);
    }
  }

  LoanPolicy withDueDateSchedules(FixedDueDateSchedules fixedDueDateSchedules) {
    return new LoanPolicy(this, fixedDueDateSchedules);
  }

  //TODO: potentially remove this, when builder can create class or JSON representation
  LoanPolicy withDueDateSchedules(JsonObject fixedDueDateSchedules) {
    return new LoanPolicy(this, new FixedDueDateSchedules(fixedDueDateSchedules));
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
