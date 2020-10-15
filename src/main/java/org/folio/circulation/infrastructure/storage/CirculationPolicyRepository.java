package org.folio.circulation.infrastructure.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.rules.ApplyCondition.forItem;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.ApplyCondition;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.CirculationRulesProcessor;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public abstract class CirculationPolicyRepository<T> {
  public static final String LOCATION_ID_NAME = "location_id";
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected final CollectionResourceClient policyStorageClient;
  protected final CirculationRulesProcessor circulationRulesProcessor;

  protected CirculationPolicyRepository(CollectionResourceClient policyStorageClient,
    Clients clients) {

    this.policyStorageClient = policyStorageClient;
    this.circulationRulesProcessor = clients.circulationRulesProcessor();
  }

  public CompletableFuture<Result<T>> lookupPolicy(Loan loan) {
    return lookupPolicy(loan.getItem(), loan.getUser());
  }

  public CompletableFuture<Result<T>> lookupPolicy(Request request) {
    return lookupPolicy(request.getItem(), request.getRequester());
  }

  public CompletableFuture<Result<T>> lookupPolicy(
    Item item, User user) {

    return lookupPolicyId(item, user)
      .thenComposeAsync(r -> r.after(ruleMatchEntity -> lookupPolicy(
        ruleMatchEntity.getPolicyId(), ruleMatchEntity.getAppliedRuleConditions())));
  }

  private Result<T> mapToPolicy(JsonObject json, AppliedRuleConditions ruleConditionsEntity) {
    if (log.isDebugEnabled()) {
      log.debug("Mapping json to policy {}", json.encodePrettily());
    }

    return toPolicy(json, ruleConditionsEntity);
  }

  public CompletableFuture<Result<T>> lookupPolicy(String policyId, AppliedRuleConditions conditionsEntity) {
    log.info("Looking up policy with id {}", policyId);

    return SingleRecordFetcher.json(policyStorageClient, "circulation policy",
      response -> failedDueToServerError(getPolicyNotFoundErrorMessage(policyId)))
      .fetch(policyId)
      .thenApply(result -> result.next(json -> mapToPolicy(json, conditionsEntity)));
  }

  public CompletableFuture<Result<CirculationRuleMatch>> lookupPolicyId(Item item, User user) {
    if (item.isNotFound()) {
      return completedFuture(failedDueToServerError(
        "Unable to apply circulation rules for unknown item"));
    }

    if (item.doesNotHaveHolding()) {
      return completedFuture(failedDueToServerError(
        "Unable to apply circulation rules for unknown holding"));
    }

    final var applyCondition = forItem(item, user);

    log.info("Applying circulation rules for conditions: {}", applyCondition);
    return getPolicyAndMatch(applyCondition);
  }

  protected abstract String getPolicyNotFoundErrorMessage(String policyId);

  protected abstract Result<T> toPolicy(JsonObject representation, AppliedRuleConditions ruleConditionsEntity);

  protected abstract String fetchPolicyId(JsonObject jsonObject);

  protected abstract CompletableFuture<Result<CirculationRuleMatch>> getPolicyAndMatch(
    ApplyCondition applyCondition);
}
