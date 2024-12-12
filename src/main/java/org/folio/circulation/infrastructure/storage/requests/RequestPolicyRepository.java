package org.folio.circulation.infrastructure.storage.requests;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.circulation.rules.ExecutableRules.MATCH_FAIL_MSG_REGEX;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.asJson;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.rules.CirculationRuleCriteria;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.CirculationRulesProcessor;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class RequestPolicyRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CirculationRulesProcessor circulationRulesProcessor;
  private final CollectionResourceClient requestPoliciesStorageClient;

  public RequestPolicyRepository(Clients clients) {
    this.requestPoliciesStorageClient = clients.requestPoliciesStorage();
    this.circulationRulesProcessor = clients.circulationRulesProcessor();
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> lookupRequestPolicy(
    RequestAndRelatedRecords relatedRecords) {

    log.debug("lookupRequestPolicy:: parameters relatedRecords: {}", relatedRecords);

    Request request = relatedRecords.getRequest();

    return lookupRequestPolicy(request.getItem(), request.getRequester())
      .thenApply(result -> result.map(relatedRecords::withRequestPolicy));
  }

  public CompletableFuture<Result<Request>> lookupRequestPolicies(Request request) {
    log.debug("lookupRequestPolicies:: parameters request: {}", request);

    return allOf(request.getInstanceItems(), Item::getItemId,
      item -> lookupRequestPolicy(item, request.getRequester()))
      .thenApply(r -> r.map(request::withInstanceItemsRequestPolicies));
  }

  public CompletableFuture<Result<RequestPolicy>> lookupRequestPolicy(Item item, User user) {
    log.debug("lookupRequestPolicy:: parameters item: {}, user: {}", item, user);
    return lookupRequestPolicyId(item, user)
      .thenComposeAsync(r -> r.after(this::lookupRequestPolicyById))
      .thenApply(result -> result.map(RequestPolicy::from));
  }

  public CompletableFuture<Result<Map<RequestPolicy, Set<Item>>>> lookupRequestPolicies(
    Collection<Item> items, String patronGroupId) {

    log.debug("lookupRequestPolicies:: parameters items: {}, patronGroupId: {}",
      items::size, () -> asJson(patronGroupId));

    Map<CirculationRuleCriteria, Set<Item>> criteriaMap = items.stream()
      .map(item -> new CirculationRuleCriteria(item, patronGroupId))
      .collect(toMap(identity(), criteria -> Set.of(criteria.getItem()), itemsMergeOperator()));

    return allOf(criteriaMap.entrySet(), entry -> lookupRequestPolicyId(entry.getKey())
      .thenApply(r -> r.map(id -> Pair.of(id, entry.getValue()))))
      .thenApply(r -> r.map(pair -> pair.stream()
        .collect(toMap(Pair::getKey, Pair::getValue, itemsMergeOperator()))))
      .thenCompose(r -> r.after(this::lookupRequestPolicies));
  }

  public CompletableFuture<Result<RequestPolicy>> lookupRequestPolicy(String patronGroupId) {
    // Circulation rules need to be executed with the patron group parameter only.
    // All the item-related parameters should be random UUIDs.
    return lookupRequestPolicyId(UUID.randomUUID().toString(), patronGroupId,
      UUID.randomUUID().toString(), UUID.randomUUID().toString())
      .thenCompose(r -> r.after(this::lookupRequestPolicyById))
      .thenApply(result -> result.map(RequestPolicy::from));
  }

  private BinaryOperator<Set<Item>> itemsMergeOperator() {
    return (items1, items2) -> Stream.concat(items1.stream(), items2.stream())
      .collect(Collectors.toSet());
  }

  private CompletableFuture<Result<JsonObject>> lookupRequestPolicyById(
    String requestPolicyId) {

    log.debug("lookupRequestPolicy:: parameters requestPolicyId: {}", requestPolicyId);
    return SingleRecordFetcher.json(requestPoliciesStorageClient, "request policy",
        response -> failedDueToServerError(format(
          "Request policy %s could not be found, please check circulation rules", requestPolicyId)))
      .fetch(requestPolicyId);
  }

  private CompletableFuture<Result<Map<RequestPolicy, Set<Item>>>>
  lookupRequestPolicies(Map<String, Set<Item>> requestPolicyIdMap) {

    FindWithMultipleCqlIndexValues<RequestPolicy> finder = findWithMultipleCqlIndexValues(
      requestPoliciesStorageClient, "requestPolicies", RequestPolicy::from);

    return finder.findByIds(requestPolicyIdMap.keySet())
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(requestPolicies -> requestPolicies.stream()
        .collect(toMap(identity(), policy -> requestPolicyIdMap.get(policy.getId())))));
  }

  private CompletableFuture<Result<String>> lookupRequestPolicyId(
    Item item, User user) {

    log.debug("lookupRequestPolicyId:: parameters item: {}, user: {}", item, user);
    if (item.isNotFound()) {
      log.info("lookupRequestPolicyId:: item is not found");
      return completedFuture(failedDueToServerError(
        "Unable to find matching request rules for unknown item"));
    }

    String materialTypeId = item.getMaterialTypeId();
    String patronGroupId = user.getPatronGroupId();
    String loanTypeId = item.getLoanTypeId();
    String locationId = item.getEffectiveLocationId();

    log.info(
      "Applying request rules for material type: {}, patron group: {}, loan type: {}, location: {}",
      materialTypeId, patronGroupId, loanTypeId, locationId);

    return lookupRequestPolicyId(materialTypeId, patronGroupId, loanTypeId, locationId);
  }

  private CompletableFuture<Result<String>> lookupRequestPolicyId(
    CirculationRuleCriteria criteria) {

    log.debug("lookupRequestPolicyId:: parameters criteria: {}", criteria);
    return lookupRequestPolicyId(criteria.getMaterialTypeId(),
      criteria.getPatronGroupId(), criteria.getLoanTypeId(), criteria.getLocationId());
  }

  private CompletableFuture<Result<String>> lookupRequestPolicyId(String materialTypeId,
    String patronGroupId, String loanTypeId, String locationId) {

    log.debug("lookupRequestPolicyId:: parameters materialTypeId: {}, patronGroupId: {}," +
      "loanTypeId: {}, locationId: {}", materialTypeId, patronGroupId, loanTypeId, locationId);

    var params = new RulesExecutionParameters(loanTypeId, locationId, materialTypeId, patronGroupId, null);
    return circulationRulesProcessor.getRequestPolicyAndMatch(params)
      .thenCompose(this::processRulesResponse);
  }

  private CompletableFuture<Result<String>> processRulesResponse(Result<CirculationRuleMatch> response) {
    log.debug("processRulesResponse:: parameters response successful: {}", response.succeeded());
    final CompletableFuture<Result<String>> future = new CompletableFuture<>();

    if (response.succeeded()) {
      log.info("processRulesResponse:: successfully applied request rules");
      future.complete(succeeded(response.value().getPolicyId()));
    } else {
      if (response.cause() instanceof ServerErrorFailure e && e.getReason().matches(MATCH_FAIL_MSG_REGEX)) {
        log.info("processRulesResponse:: no matching request rules found");
        future.complete(failedDueToServerError("Unable to find matching request rules"));
      } else {
        log.info("processRulesResponse:: failed to apply request rules");
        future.complete(failed(response.cause()));
      }
    }

    return future;
  }
}
