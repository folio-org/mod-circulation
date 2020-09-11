package org.folio.circulation.infrastructure.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.CirculationRulesClient;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public abstract class CirculationPolicyRepository<T> {
  private static final String APPLIED_RULE_CONDITIONS = "appliedRuleConditions";
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CirculationRulesClient circulationRulesClient;
  protected final CollectionResourceClient policyStorageClient;

  protected CirculationPolicyRepository(
    CirculationRulesClient circulationRulesClient,
    CollectionResourceClient policyStorageClient) {
    this.circulationRulesClient = circulationRulesClient;
    this.policyStorageClient = policyStorageClient;
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

    String loanTypeId = item.determineLoanTypeForItem();
    String locationId = item.getLocationId();
    String materialTypeId = item.getMaterialTypeId();
    String patronGroupId = user.getPatronGroupId();

    log.info(
      "Applying circulation rules for material type: {}, patron group: {}, loan type: {}, location: {}",
      materialTypeId, patronGroupId, loanTypeId, locationId);

    final CompletableFuture<Result<Response>> circulationRulesResponse =
      circulationRulesClient.applyRules(loanTypeId, locationId, materialTypeId, patronGroupId);

    return circulationRulesResponse
      .thenCompose(r -> r.after(this::processRulesResponse));
  }

  private CompletableFuture<Result<CirculationRuleMatch>> processRulesResponse(Response response) {
    final CompletableFuture<Result<CirculationRuleMatch>> future = new CompletableFuture<>();

    if (response.getStatusCode() == 404) {
      future.complete(failedDueToServerError("Unable to apply circulation rules"));
    } else if (response.getStatusCode() != 200) {
      future.complete(failed(new ForwardOnFailure(response)));
    } else {
      log.info("Rules response {}", response.getBody());

      String policyId = fetchPolicyId(response.getJson());
      boolean isItemTypePresent = response.getJson().getJsonObject(APPLIED_RULE_CONDITIONS)
        .getBoolean("materialTypeMatch");
      boolean isLoanTypePresent = response.getJson().getJsonObject(APPLIED_RULE_CONDITIONS)
        .getBoolean("loanTypeMatch");
      boolean isPatronGroupPresent = response.getJson().getJsonObject(APPLIED_RULE_CONDITIONS)
        .getBoolean("patronGroupMatch");

      log.info("Policy to fetch based upon rules {}", policyId);

      future.complete(succeeded(new CirculationRuleMatch(
        policyId, new AppliedRuleConditions(isItemTypePresent, isLoanTypePresent, isPatronGroupPresent))));
    }

    return future;
  }

  protected abstract String getPolicyNotFoundErrorMessage(String policyId);

  protected abstract Result<T> toPolicy(JsonObject representation, AppliedRuleConditions ruleConditionsEntity);

  protected abstract String fetchPolicyId(JsonObject jsonObject);
}
