package org.folio.circulation.resources;

import org.folio.circulation.rules.CirculationRulesProcessor;

import io.vertx.core.http.HttpClient;

public class LostItemCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public LostItemCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client,
      CirculationRulesProcessor::getLostItemPolicyAndMatch,
      CirculationRulesProcessor::getLostItemPolicies);
  }

  @Override
  protected String getPolicyIdKey() {
    return "lostItemPolicyId";
  }
}
