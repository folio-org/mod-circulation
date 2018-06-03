package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;

public class LoanPolicy {
  private final JsonObject representation;
  private final FixedDueDateSchedules fixedDueDateSchedules;

  private LoanPolicy(JsonObject representation) {
    this(representation, null);
  }

  LoanPolicy(
    JsonObject representation,
    FixedDueDateSchedules fixedDueDateSchedules) {

    this.representation = representation;
    this.fixedDueDateSchedules = fixedDueDateSchedules;
  }

  static LoanPolicy from(JsonObject representation) {
    return new LoanPolicy(representation);
  }

  //TODO: make this have similar signature to renew
  public HttpResult<DateTime> calculateInitialDueDate(Loan loan) {
    return determineStrategy().calculateInitialDueDate(loan);
  }

  public HttpResult<Loan> renew(Loan loan) {
    return determineStrategy().calculateRenewalDueDate(loan)
      .map(dueDate -> loan.renew(dueDate, getId()));
  }

  private DueDateStrategy determineStrategy() {
    final String loanPolicyId = representation.getString("id");
    final String loanPolicyName = representation.getString("name");

    final JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    //TODO: Temporary until have better logic for missing loans policy
    if(loansPolicy == null) {
      return new UnknownDueDateStrategy(loanPolicyId, loanPolicyName, "");
    }

    final String profileId = loansPolicy.getString("profileId");

    if(StringUtils.equalsIgnoreCase(profileId, "Rolling")) {
      final String interval = getNestedStringProperty(loansPolicy, "period", "intervalId");
      final Integer duration = getNestedIntegerProperty(loansPolicy, "period", "duration");

      return new RollingDueDateStrategy(loanPolicyId, loanPolicyName,
        interval, duration, fixedDueDateSchedules);
    }
    else if(StringUtils.equalsIgnoreCase(profileId, "Fixed")) {
      return new FixedScheduleDueDateStrategy(loanPolicyId, loanPolicyName,
        fixedDueDateSchedules);
    }
    else {
      return new UnknownDueDateStrategy(loanPolicyId, loanPolicyName, profileId);
    }
  }

  LoanPolicy withDueDateSchedules(FixedDueDateSchedules fixedDueDateSchedules) {
    return new LoanPolicy(representation, fixedDueDateSchedules);
  }

  //TODO: potentially remove this, when builder can create class or JSON representation
  LoanPolicy withDueDateSchedules(JsonObject fixedDueDateSchedules) {
    return withDueDateSchedules(new FixedDueDateSchedules(fixedDueDateSchedules));
  }

  public String getId() {
    return representation.getString("id");
  }

  String getLoansFixedDueDateScheduleId() {
    return getNestedStringProperty(representation, "loansPolicy",
      "fixedDueDateScheduleId");
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
