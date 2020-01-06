package org.folio.circulation.rules;

import java.util.List;

public class CirculationRulePolicyIdEntity {

  private final String policyId;
  private final List<String> ruleConditions;

  public CirculationRulePolicyIdEntity(String policyId, List<String> ruleConditions) {
    this.policyId = policyId;
    this.ruleConditions = ruleConditions;
  }

  public String getPolicyId() {
    return policyId;
  }

  public List<String> getRuleConditions() {
    return ruleConditions;
  }

  @Override
  public String toString() {
    return "PolicyId{" +
      "policyId='" + policyId + '\'' +
      ", ruleConditions=" + ruleConditions +
      '}';
  }
}
