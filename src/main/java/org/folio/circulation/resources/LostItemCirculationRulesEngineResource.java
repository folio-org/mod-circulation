package org.folio.circulation.resources;

import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.Drools;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;

public class LostItemCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

    public LostItemCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
        super(applyPath, applyAllPath, client);
    }

    @Override
    protected CirculationRuleMatch getPolicyIdAndRuleMatch(MultiMap params, Drools drools, Location location) {
        return CirculationRulesProcessor.getLostItemPolicyAndMatch(drools, params, location);
    }

    @Override
    protected String getPolicyIdKey() {
        return "lostItemPolicyId";
    }

    @Override
    protected JsonArray getPolicies(MultiMap params, Drools drools, Location location) {
        return CirculationRulesProcessor.getLostItemPolicies(drools, params, location);
    }
}
