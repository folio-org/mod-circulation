package org.folio.circulation.rules;

import java.util.List;

public class CirculationRuleMatchEntity {

  private final String policyId;
  private final List<String> appliedRuleConditions;

  public CirculationRuleMatchEntity(String policyId, List<String> appliedRuleConditions) {
    this.policyId = policyId;
    this.appliedRuleConditions = appliedRuleConditions;
  }

  public String getPolicyId() {
    return policyId;
  }

  public List<String> getAppliedRuleConditions() {
    return appliedRuleConditions;
  }
}
