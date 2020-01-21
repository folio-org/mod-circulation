package org.folio.circulation.resources;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.Drools;
import org.folio.circulation.support.Result;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.Result.succeeded;

public class LostItemCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

    public LostItemCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
        super(applyPath, applyAllPath, client);
    }

    @Override
    protected CompletableFuture<Result<CirculationRuleMatch>> getPolicyIdAndRuleMatch(MultiMap params, Drools drools, Location location) {
        return CompletableFuture.completedFuture(Result.succeeded(drools.lostItemPolicy(params, location)));
    }

    @Override
    protected String getPolicyIdKey() {
        return "lostItemPolicyId";
    }

    @Override
    protected CompletableFuture<Result<JsonArray>> getPolicies(MultiMap params, Drools drools, Location location) {
        return CompletableFuture.completedFuture(succeeded(drools.loanPolicies(params, location)));
    }
}
