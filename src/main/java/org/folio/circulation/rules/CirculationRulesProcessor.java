package org.folio.circulation.rules;

import static org.folio.circulation.support.results.Result.combined;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;

public class CirculationRulesProcessor {
  private final String tenantId;
  private final CollectionResourceClient circulationRulesStorage;
  private final CollectionResourceClient locationStorageClient;

  public CirculationRulesProcessor(String tenantId, CollectionResourceClient circulationRulesClient,
    CollectionResourceClient locationClient) {

    this.tenantId = tenantId;
    this.circulationRulesStorage = circulationRulesClient;
    this.locationStorageClient = locationClient;
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getLoanPolicyAndMatch(
    ApplyCondition condition) {

    return triggerRules(condition,
      (drools, cond) -> drools.loanPolicy(cond.toMap(), cond.getLocation()));
  }

  public CompletableFuture<Result<JsonArray>> getLoanPolicies(ApplyCondition condition) {
    return triggerRules(condition,
      (drools, cond) -> drools.loanPolicies(cond.toMap(), cond.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getLostItemPolicyAndMatch(
    ApplyCondition condition) {

    return triggerRules(condition,
      (drools, cond) -> drools.lostItemPolicy(cond.toMap(), cond.getLocation()));
  }

  public CompletableFuture<Result<JsonArray>> getLostItemPolicies(ApplyCondition condition) {
    return triggerRules(condition,
      (drools, cond) -> drools.lostItemPolicies(cond.toMap(), cond.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getNoticePolicyAndMatch(
    ApplyCondition condition) {

    return triggerRules(condition,
      (drools, cond) -> drools.noticePolicy(cond.toMap(), cond.getLocation()));
  }

  public CompletableFuture<Result<JsonArray>> getNoticePolicies(ApplyCondition condition) {
    return triggerRules(condition,
      (drools, cond) -> drools.noticePolicies(cond.toMap(), cond.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getOverduePolicyAndMatch(
    ApplyCondition condition) {

    return triggerRules(condition,
      (drools, cond) -> drools.overduePolicy(cond.toMap(), cond.getLocation()));
  }

  public CompletableFuture<Result<JsonArray>> getOverduePolicies(ApplyCondition condition) {
    return triggerRules(condition,
      (drools, cond) -> drools.overduePolicies(cond.toMap(), cond.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getRequestPolicyAndMatch(
    ApplyCondition condition) {

    return triggerRules(condition,
      (drools, cond) -> drools.requestPolicy(cond.toMap(), cond.getLocation()));
  }

  public CompletableFuture<Result<JsonArray>> getRequestPolicies(ApplyCondition condition) {
    return triggerRules(condition,
      (drools, cond) -> drools.requestPolicies(cond.toMap(), cond.getLocation()));
  }

  private <T> CompletableFuture<Result<T>> triggerRules(ApplyCondition condition,
    BiFunction<Drools, ApplyCondition, T> droolsFunction) {

    final var droolsFuture = CirculationRulesCache.getInstance()
      .getDrools(tenantId, circulationRulesStorage);

    return fetchLocation(condition).thenCombine(droolsFuture, combined(
      (newConditions, drools) -> succeeded(droolsFunction.apply(drools, newConditions))));
  }

  private CompletableFuture<Result<ApplyCondition>> fetchLocation(ApplyCondition condition) {
    if (condition.getLocation() != null) {
      return ofAsync(() -> condition);
    }

    return FetchSingleRecord.<Location>forRecord("location")
      .using(locationStorageClient)
      .mapTo(Location::from)
      .whenNotFound(failed(new ServerErrorFailure("Can`t find location")))
      .fetch(condition.getLocationId())
      .thenApply(r -> r.map(condition::withLocation));
  }
}
