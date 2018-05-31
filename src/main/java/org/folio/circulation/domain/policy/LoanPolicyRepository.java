package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

public class LoanPolicyRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final LoanRulesClient loanRulesClient;
  private final CollectionResourceClient loanPoliciesStorageClient;
  private final CollectionResourceClient fixedDueDateSchedulesStorageClient;

  public LoanPolicyRepository(Clients clients) {
    loanRulesClient = clients.loanRules();
    loanPoliciesStorageClient = clients.loanPoliciesStorage();
    fixedDueDateSchedulesStorageClient = clients.fixedDueDateSchedules();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> lookupLoanPolicy(
    LoanAndRelatedRecords relatedRecords) {

    return lookupLoanPolicyId(
      relatedRecords.getInventoryRecords(), relatedRecords.getRequestingUser())
      .thenComposeAsync(r -> r.after(this::lookupLoanPolicy))
      .thenApply(result -> result.map(this::toLoanPolicy))
      .thenComposeAsync(r -> r.after(this::lookupSchedules))
      .thenApply(result -> result.map(relatedRecords::withLoanPolicy));
  }

  private LoanPolicy toLoanPolicy(JsonObject representation) {
    return new LoanPolicy(representation, null);
  }

  private CompletableFuture<HttpResult<LoanPolicy>> lookupSchedules(LoanPolicy loanPolicy) {
    final String fixedDueDateScheduleId = loanPolicy.getLoansFixedDueDateScheduleId();

    if(fixedDueDateScheduleId != null) {
      final SingleRecordFetcher fetcher = new SingleRecordFetcher(
        fixedDueDateSchedulesStorageClient,
        "fixed due date schedule");

      return fetcher
        .fetchSingleRecord(fixedDueDateScheduleId)
        .thenApply(r -> r.map(FixedDueDateSchedules::new))
        .thenApply(r -> r.map(loanPolicy::withDueDateSchedules));
    }
    else {
      return CompletableFuture.completedFuture(HttpResult.success(loanPolicy));
    }
  }

  private CompletableFuture<HttpResult<JsonObject>> lookupLoanPolicy(
    String loanPolicyId) {

    return new SingleRecordFetcher(loanPoliciesStorageClient, "loan policy")
      .fetchSingleRecord(loanPolicyId,
        () -> HttpResult.failure(new ServerErrorFailure(
          String.format("Loan policy %s could not be found, please check loan rules", loanPolicyId))));
  }

  private CompletableFuture<HttpResult<String>> lookupLoanPolicyId(
    InventoryRecords inventoryRecords,
    JsonObject user) {

    CompletableFuture<HttpResult<String>> findLoanPolicyCompleted
      = new CompletableFuture<>();

    if(inventoryRecords.isNotFound()) {
      return CompletableFuture.completedFuture(HttpResult.failure(
        new ServerErrorFailure("Unable to apply loan rules for unknown item")));
    }

    if(inventoryRecords.doesNotHaveHolding()) {
      return CompletableFuture.completedFuture(HttpResult.failure(
        new ServerErrorFailure("Unable to apply loan rules for unknown holding")));
    }

    String loanTypeId = inventoryRecords.determineLoanTypeForItem();
    String locationId = inventoryRecords.getLocationId();

    String materialTypeId = inventoryRecords.getMaterialTypeId();

    String patronGroupId = user.getString("patronGroup");

    CompletableFuture<Response> loanRulesResponse = new CompletableFuture<>();

    log.info(
      "Applying loan rules for material type: {}, patron group: {}, loan type: {}, location: {}",
      materialTypeId, patronGroupId, loanTypeId, locationId);

    loanRulesClient.applyRules(loanTypeId, locationId, materialTypeId,
      patronGroupId, ResponseHandler.any(loanRulesResponse));

    loanRulesResponse.thenAcceptAsync(response -> {
      if (response.getStatusCode() == 404) {
        findLoanPolicyCompleted.complete(HttpResult.failure(
          new ServerErrorFailure("Unable to apply loan rules")));
      } else if (response.getStatusCode() != 200) {
        findLoanPolicyCompleted.complete(HttpResult.failure(
          new ForwardOnFailure(response)));
      } else {
        String policyId = response.getJson().getString("loanPolicyId");
        findLoanPolicyCompleted.complete(HttpResult.success(policyId));
      }
    });

    return findLoanPolicyCompleted;
  }
}
