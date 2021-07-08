package org.folio.circulation.rules;

import static org.folio.circulation.support.results.Result.combined;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import lombok.val;

public class CirculationRulesProcessor {
  private static final Logger log = LogManager.getLogger(CirculationRulesProcessor.class);

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
    RulesExecutionParameters params) {

    return executeRules(params, ExecutableRules::determineLoanPolicy);
  }

  public CompletableFuture<Result<JsonArray>> getLoanPolicies(RulesExecutionParameters params) {
    return triggerRules(params,
      (drools, newParams) -> drools.loanPolicies(newParams.toMap(), newParams.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getLostItemPolicyAndMatch(
    RulesExecutionParameters params) {

    return executeRules(params, ExecutableRules::determineLostItemPolicy);
  }

  public CompletableFuture<Result<JsonArray>> getLostItemPolicies(RulesExecutionParameters params) {
    return triggerRules(params,
      (drools, newParams) -> drools.lostItemPolicies(newParams.toMap(), newParams.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getNoticePolicyAndMatch(
    RulesExecutionParameters params) {

    return executeRules(params, ExecutableRules::determineNoticePolicy);
  }

  public CompletableFuture<Result<JsonArray>> getNoticePolicies(RulesExecutionParameters params) {
    return triggerRules(params,
      (drools, newParams) -> drools.noticePolicies(newParams.toMap(), newParams.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getOverduePolicyAndMatch(
    RulesExecutionParameters params) {

    return executeRules(params, ExecutableRules::determineOverduePolicy);
  }

  public CompletableFuture<Result<JsonArray>> getOverduePolicies(RulesExecutionParameters params) {
    return triggerRules(params,
      (drools, newParams) -> drools.overduePolicies(newParams.toMap(), newParams.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getRequestPolicyAndMatch(
    RulesExecutionParameters params) {

    return executeRules(params, ExecutableRules::determineRequestPolicy);
  }

  public CompletableFuture<Result<JsonArray>> getRequestPolicies(RulesExecutionParameters params) {
    return triggerRules(params,
      (drools, newParams) -> drools.requestPolicies(newParams.toMap(), newParams.getLocation()));
  }

  private <T> CompletableFuture<Result<T>> triggerRules(RulesExecutionParameters params,
    BiFunction<Drools, RulesExecutionParameters, T> droolsFunction) {

    val rulesFuture = CirculationRulesCache.getInstance()
      .getDrools(tenantId, circulationRulesStorage);

    return fetchLocation(params).thenCombine(rulesFuture, combined(
      (newParams, drools) -> {
        log.info("Applying circulation rules with parameters: {}", newParams);
        return succeeded(droolsFunction.apply(drools, newParams));
      }));
  }

  private <T> CompletableFuture<Result<T>> executeRules(RulesExecutionParameters params,
    BiFunction<ExecutableRules, RulesExecutionParameters, Result<T>> rulesExecutor) {

    val rulesFuture = CirculationRulesCache.getInstance()
      .getExecutableRules(tenantId, circulationRulesStorage);

    return fetchLocation(params)
      .thenCombine(rulesFuture, combined((parametersWithLocation, rules) ->
        rulesExecutor.apply(rules, parametersWithLocation)));
  }

  private CompletableFuture<Result<RulesExecutionParameters>> fetchLocation(RulesExecutionParameters params) {
    if (params.getLocation() != null) {
      return ofAsync(() -> params);
    }

    return FetchSingleRecord.<Location>forRecord("location")
      .using(locationStorageClient)
      .mapTo(Location::from)
      .whenNotFound(failed(new ServerErrorFailure("Can`t find location")))
      .fetch(params.getLocationId())
      .thenApply(r -> r.map(params::withLocation));
  }
}
