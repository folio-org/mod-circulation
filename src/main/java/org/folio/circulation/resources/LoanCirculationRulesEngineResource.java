package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.Drools;
import org.folio.circulation.support.Result;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;

/**
 * The circulation rules engine calculates the loan policy based on
 * item type, loan type, patron type and location.
 */
public class LoanCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public LoanCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client);
  }

  @Override
  protected CompletableFuture<Result<Pair<String, Integer>>> getPolicyIdAndRuleLine(MultiMap params, Drools drools, Location location) {
    return CompletableFuture.completedFuture(Result.succeeded(drools.loanPolicy(params, location)));
  }

  @Override
  protected String getPolicyIdKey() {
    return "loanPolicyId";
  }

  @Override
  protected CompletableFuture<Result<JsonArray>> getPolicies(MultiMap params, Drools drools, Location location) {
    return CompletableFuture.completedFuture(succeeded(drools.loanPolicies(params, location)));
  }
}
