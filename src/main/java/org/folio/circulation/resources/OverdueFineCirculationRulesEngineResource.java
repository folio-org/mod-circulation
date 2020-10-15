package org.folio.circulation.resources;

import org.folio.circulation.rules.CirculationRulesProcessor;

import io.vertx.core.http.HttpClient;


public class OverdueFineCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public OverdueFineCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client,
      CirculationRulesProcessor::getOverduePolicyAndMatch,
      CirculationRulesProcessor::getOverduePolicies);
  }

  @Override
  protected String getPolicyIdKey() {
    return "overdueFinePolicyId";
  }
}
