package org.folio.circulation.resources;

import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.Drools;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;

/**
 * The circulation rules engine calculates the loan policy based on
 * item type, loan type, patron type and shelving location.
 */
public class NoticeCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public NoticeCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client);
  }

  @Override
  protected CirculationRuleMatch getPolicyIdAndRuleMatch(
    MultiMap params, Drools drools, Location location) {
    return CirculationRulesProcessor.getNoticePolicyAndMatch(drools, params, location);
  }

  @Override
  protected String getPolicyIdKey() {
    return "noticePolicyId";
  }

  @Override
  protected  JsonArray getPolicies(MultiMap params, Drools drools, Location location) {
    return CirculationRulesProcessor.getNoticePolicies(drools, params, location);
  }
}
