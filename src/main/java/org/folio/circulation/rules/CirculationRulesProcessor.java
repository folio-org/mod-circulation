package org.folio.circulation.rules;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.combined;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.storage.mappers.LocationMapper;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
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

    log.debug("getLoanPolicyAndMatch:: parameters params: {}", params);

    return executeRules(params, ExecutableRules::determineLoanPolicy);
  }

  public CompletableFuture<Result<JsonArray>> getLoanPolicies(RulesExecutionParameters params) {
    log.debug("getLoanPolicies:: parameters params: {}", params);

    return triggerRules(params,
      (drools, newParams) -> drools.loanPolicies(newParams.toMap(), newParams.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getLostItemPolicyAndMatch(
    RulesExecutionParameters params) {

    log.debug("getLostItemPolicyAndMatch:: parameters params: {}", params);

    return executeRules(params, ExecutableRules::determineLostItemPolicy);
  }

  public CompletableFuture<Result<JsonArray>> getLostItemPolicies(RulesExecutionParameters params) {
    log.debug("getLostItemPolicies:: parameters params: {}", params);

    return triggerRules(params,
      (drools, newParams) -> drools.lostItemPolicies(newParams.toMap(), newParams.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getNoticePolicyAndMatch(
    RulesExecutionParameters params) {

    log.debug("getNoticePolicyAndMatch:: parameters params: {}", params);

    return executeRules(params, ExecutableRules::determineNoticePolicy);
  }

  public CompletableFuture<Result<JsonArray>> getNoticePolicies(RulesExecutionParameters params) {
    log.debug("getNoticePolicies:: parameters params: {}", params);

    return triggerRules(params,
      (drools, newParams) -> drools.noticePolicies(newParams.toMap(), newParams.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getOverduePolicyAndMatch(
    RulesExecutionParameters params) {

    log.debug("getOverduePolicyAndMatch:: parameters params: {}", params);

    return executeRules(params, ExecutableRules::determineOverduePolicy);
  }

  public CompletableFuture<Result<JsonArray>> getOverduePolicies(RulesExecutionParameters params) {
    log.debug("getOverduePolicies:: parameters params: {}", params);

    return triggerRules(params,
      (drools, newParams) -> drools.overduePolicies(newParams.toMap(), newParams.getLocation()));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> getRequestPolicyAndMatch(
    RulesExecutionParameters params) {

    log.debug("getRequestPolicyAndMatch:: parameters params: {}", params);

    return executeRules(params, ExecutableRules::determineRequestPolicy);
  }

  public CompletableFuture<Result<JsonArray>> getRequestPolicies(RulesExecutionParameters params) {
    log.debug("getRequestPolicies:: parameters params: {}", params);

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

  private CompletableFuture<Result<RulesExecutionParameters>> fetchLocation(
    RulesExecutionParameters params) {

    log.debug("fetchLocation:: parameters params: {}", params);

    if (params.getLocation() != null) {
      log.info("fetchLocation:: location is not null");
      return ofAsync(() -> params);
    }

    return FetchSingleRecord.<Location>forRecord("location")
      .using(locationStorageClient)
      .mapTo(new LocationMapper()::toDomain)
      .whenNotFound(failedValidation("Cannot find location", "location_id", params.getLocationId()))
      .fetch(params.getLocationId())
      .thenApply(r -> r.map(params::withLocation))
      .thenApply(r -> r.mapFailure(failure -> succeeded(params)));
  }
}
