package org.folio.circulation.resources;

import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.CirculationRuleMatch;
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
  public CirculationRuleMatch getPolicyIdAndRuleMatch(
    MultiMap params, Drools drools, Location location) {
    return CirculationRulesProcessor.getRequestPolicyAndMatch(drools, params, location);
  }

  @Override
  protected String getPolicyIdKey() {
    return "requestPolicyId";
  }

  @Override
  protected JsonArray getPolicies(MultiMap params, Drools drools, Location location) {
    return CirculationRulesProcessor.getRequestPolicies(drools, params, location);
  }
}
