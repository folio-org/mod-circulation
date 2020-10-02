package org.folio.circulation.resources;

import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.Drools;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;


public class OverdueFineCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

    public OverdueFineCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
        super(applyPath, applyAllPath, client);
    }

    @Override
    protected CirculationRuleMatch getPolicyIdAndRuleMatch(
      MultiMap params, Drools drools, Location location) {
        return CirculationRulesProcessor.getOverduePolicyAndMatch(drools, params, location);
    }

    @Override
    protected String getPolicyIdKey() {
        return "overdueFinePolicyId";
    }

    @Override
    protected JsonArray getPolicies(MultiMap params, Drools drools, Location location) {
        return CirculationRulesProcessor.getOverduePolicies(drools, params, location);
    }
}
