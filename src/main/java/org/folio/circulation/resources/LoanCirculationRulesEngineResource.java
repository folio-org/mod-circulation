package org.folio.circulation.resources;

import org.folio.circulation.rules.CirculationRulesProcessor;

import io.vertx.core.http.HttpClient;

/**
 * The circulation rules engine calculates the loan policy based on
 * item type, loan type, patron type and location.
 */
public class LoanCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public LoanCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client,
      CirculationRulesProcessor::getLoanPolicyAndMatch,
      CirculationRulesProcessor::getLoanPolicies);
  }

  @Override
  protected String getPolicyIdKey() {
    return "loanPolicyId";
  }
}
