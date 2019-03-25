package org.folio.circulation.resources;

import org.folio.circulation.rules.Drools;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;

/**
 * The circulation rules engine calculates the request policy based on
 * item type, request type, patron type and shelving location.
 */
public class RequestCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public RequestCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client);
  }

  @Override
  protected String getPolicyId(MultiMap params, Drools drools) {
    return drools.requestPolicy(params);
  }

  @Override
  protected String getPolicyIdKey() {
    return "requestPolicyId";
  }

  @Override
  protected JsonArray getPolicies(MultiMap params, Drools drools) {
    return drools.requestPolicies(params);
  }
}
