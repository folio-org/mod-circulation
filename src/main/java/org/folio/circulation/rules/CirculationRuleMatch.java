package org.folio.circulation.rules;

import lombok.ToString;

@ToString
public class CirculationRuleMatch {

  private final String policyId;
  private final AppliedRuleConditions appliedRuleConditions;

  public CirculationRuleMatch(String policyId,
    AppliedRuleConditions appliedRuleConditions) {

    this.policyId = policyId;
    this.appliedRuleConditions = appliedRuleConditions;
  }

  public String getPolicyId() {
    return policyId;
  }

  public AppliedRuleConditions getAppliedRuleConditions() {
    return appliedRuleConditions;
  }
}
