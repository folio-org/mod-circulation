package org.folio.circulation.rules;

public class CirculationRuleMatchEntity {

  private final String policyId;
  private final AppliedRuleConditionsEntity appliedRuleConditionsEntity;

  public CirculationRuleMatchEntity(String policyId,
    AppliedRuleConditionsEntity appliedRuleConditionsEntity) {

    this.policyId = policyId;
    this.appliedRuleConditionsEntity = appliedRuleConditionsEntity;
  }

  public String getPolicyId() {
    return policyId;
  }

  public AppliedRuleConditionsEntity getAppliedRuleConditionsEntity() {
    return appliedRuleConditionsEntity;
  }
}
