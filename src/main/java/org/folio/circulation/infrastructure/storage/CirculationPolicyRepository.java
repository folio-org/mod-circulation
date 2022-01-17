package org.folio.circulation.infrastructure.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.SideEffectOnFailure.DELETE_PATRON_NOTICE;
import static org.folio.circulation.rules.RulesExecutionParameters.forItem;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.CirculationRulesProcessor;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;

public abstract class CirculationPolicyRepository<T> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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

  public CompletableFuture<Result<CirculationRuleMatch>> lookupPolicyId(Loan loan) {
    return lookupPolicyId(loan.getItem(), loan.getUser());
  }

  public CompletableFuture<Result<CirculationRuleMatch>> lookupPolicyId(Request request) {
    return lookupPolicyId(request.getItem(), request.getRequester());
  }

  public CompletableFuture<Result<CirculationRuleMatch>> lookupPolicyId(PatronNoticeEvent noticeEvent) {
    return lookupPolicyId(noticeEvent.getItem(), noticeEvent.getUser());
  }

  public CompletableFuture<Result<CirculationRuleMatch>> lookupPolicyId(Item item, User user) {
    if (item == null){
      return completedFuture(CommonFailures.failedDueToServerErrorFailureWithSideEffect(
        "Unable to apply circulation rules for item is null", DELETE_PATRON_NOTICE));
    }

    if (user == null){
      return completedFuture(CommonFailures.failedDueToServerErrorFailureWithSideEffect(
        "Unable to apply circulation rules for item with user that is null", DELETE_PATRON_NOTICE));
    }

    if (item.isNotFound()) {
      return completedFuture(CommonFailures.failedDueToServerErrorFailureWithSideEffect(
        "Unable to apply circulation rules for unknown item", DELETE_PATRON_NOTICE));
    }

    if (user.getPatronGroupId() == null) {
      log.error("PatronGroupId is null for user {}", user.getId());
      return completedFuture(CommonFailures.failedDueToServerErrorFailureWithSideEffect(
        "Unable to apply circulation rules to a user with null value as patronGroupId",
        DELETE_PATRON_NOTICE));
    }

    if (item.getLocationId() == null) {
      log.error("LocationId is null for item {}", item.getItemId());
      return completedFuture(CommonFailures.failedDueToServerErrorFailureWithSideEffect(
        "Unable to apply circulation rules to an item with null value as locationId",
        DELETE_PATRON_NOTICE));
    }

    if (item.determineLoanTypeForItem() == null) {
      log.error("LoanTypeId is null for item {}", item.getItemId());
      return completedFuture(CommonFailures.failedDueToServerErrorFailureWithSideEffect(
        "Unable to apply circulation rules to an item which loan type can not be determined",
        DELETE_PATRON_NOTICE));
    }

    if (item.getMaterialTypeId() == null) {
      log.error("MaterialTypeId is null for item {}", item.getItemId());
      return completedFuture(CommonFailures.failedDueToServerErrorFailureWithSideEffect(
        "Unable to apply circulation rules to an item with null value as materialTypeId",
        DELETE_PATRON_NOTICE));
    }

    return getPolicyAndMatch(forItem(item, user));
  }

  protected abstract String getPolicyNotFoundErrorMessage(String policyId);

  protected abstract Result<T> toPolicy(JsonObject representation, AppliedRuleConditions ruleConditionsEntity);

  protected abstract String fetchPolicyId(JsonObject jsonObject);

  protected abstract CompletableFuture<Result<CirculationRuleMatch>> getPolicyAndMatch(
    RulesExecutionParameters rulesExecutionParameters);
}
