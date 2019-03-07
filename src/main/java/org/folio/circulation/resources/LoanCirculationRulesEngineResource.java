package org.folio.circulation.resources;

import org.folio.circulation.rules.Drools;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;

/**
 * The circulation rules engine calculates the loan policy based on
 * item type, loan type, patron type and shelving location.
 */
public class LoanCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public LoanCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client);
  }

  @Override
  protected String getPolicyId(MultiMap params, Drools drools) {
    return drools.loanPolicy(params);
  }

  @Override
  protected String getPolicyIdKey() {
    return "loanPolicyId";
  }

  @Override
  protected JsonArray getPolicies(MultiMap params, Drools drools) {
    return drools.loanPolicies(params);
  }
}
