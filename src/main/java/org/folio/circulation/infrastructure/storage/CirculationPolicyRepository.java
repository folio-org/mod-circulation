package org.folio.circulation.infrastructure.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.ITEM_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOAN_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.PATRON_TYPE_ID_NAME;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.failed;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.resources.LoanCirculationRulesProcessor;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.Drools;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

public abstract class CirculationPolicyRepository<T> {
  private static final String APPLIED_RULE_CONDITIONS = "appliedRuleConditions";
  public static final String LOCATION_ID_NAME = "location_id";
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected final CollectionResourceClient policyStorageClient;
  protected final CollectionResourceClient locationsStorageClient;

  protected CirculationPolicyRepository(
    CollectionResourceClient locationsStorageClient,
    CollectionResourceClient policyStorageClient) {
    this.locationsStorageClient = locationsStorageClient;
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

    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    params.add(ITEM_TYPE_ID_NAME, item.getItemId());
    params.add(LOAN_TYPE_ID_NAME, item.determineLoanTypeForItem());
    params.add(PATRON_TYPE_ID_NAME, user.getPatronGroupId());
    params.add(LOCATION_ID_NAME, item.getLocationId());

    log.info(
      "Applying circulation rules for material type: {}, patron group: {}, loan type: {}, location: {}",
      materialTypeId, patronGroupId, loanTypeId, locationId);


    final CompletableFuture<Result<CirculationRuleMatch>> circulationRulesResponse = FetchSingleRecord.<Location>forRecord("location")
    .using(locationsStorageClient)
    .mapTo(Location::from)
    .whenNotFound(failed(new ServerErrorFailure("Can`t find location")))
    .fetch(locationId)
    .thenCompose(r -> r.after(location -> CompletableFuture.completedFuture(Result.succeeded(LoanCirculationRulesProcessor.getLoanPolicyAndMatch(new Drools("we don't have the rules here"), params, location)))));

    return circulationRulesResponse;
  }

  protected abstract String getPolicyNotFoundErrorMessage(String policyId);

  protected abstract Result<T> toPolicy(JsonObject representation, AppliedRuleConditions ruleConditionsEntity);

  protected abstract String fetchPolicyId(JsonObject jsonObject);
}
