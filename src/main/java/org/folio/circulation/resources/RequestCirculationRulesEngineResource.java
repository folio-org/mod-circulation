package org.folio.circulation.resources;

import org.folio.circulation.rules.CirculationRulesProcessor;

import io.vertx.core.http.HttpClient;

/**
 * The circulation rules engine calculates the request policy based on
 * item type, request type, patron type and shelving location.
 */
public class RequestCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public RequestCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client,
      CirculationRulesProcessor::getRequestPolicyAndMatch,
      CirculationRulesProcessor::getRequestPolicies);
  }

  @Override
  protected String getPolicyIdKey() {
    return "requestPolicyId";
  }
}
