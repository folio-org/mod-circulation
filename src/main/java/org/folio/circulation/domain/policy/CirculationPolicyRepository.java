package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.HttpResult.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.CirculationRulesClient;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public abstract class CirculationPolicyRepository<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CirculationRulesClient circulationRulesClient;
  private final CollectionResourceClient policyStorageClient;

  public CirculationPolicyRepository(
    CirculationRulesClient circulationRulesClient,
    CollectionResourceClient policyStorageClient) {
    this.circulationRulesClient = circulationRulesClient;
    this.policyStorageClient = policyStorageClient;
  }

  public CompletableFuture<HttpResult<T>> lookupPolicy(Loan loan) {
    return lookupPolicy(loan.getItem(), loan.getUser());
  }

  private CompletableFuture<HttpResult<T>> lookupPolicy(
    Item item,
    User user) {

    return lookupPolicyId(item, user)
      .thenComposeAsync(r -> r.after(this::lookupPolicy))
      .thenApply(result -> result.next(this::toPolicy));
  }

  private CompletableFuture<HttpResult<JsonObject>> lookupPolicy(String policyId) {

    return SingleRecordFetcher.json(policyStorageClient, "circulation policy",
      response -> HttpResult.failed(new ServerErrorFailure(getPolicyNotFoundErrorMessage(policyId))))
      .fetch(policyId);
  }

  private CompletableFuture<HttpResult<String>> lookupPolicyId(
    Item item,
    User user) {

    CompletableFuture<HttpResult<String>> findLoanPolicyCompleted
      = new CompletableFuture<>();

    if (item.isNotFound()) {
      return CompletableFuture.completedFuture(HttpResult.failed(
        new ServerErrorFailure("Unable to apply circulation rules for unknown item")));
    }

    if (item.doesNotHaveHolding()) {
      return CompletableFuture.completedFuture(HttpResult.failed(
        new ServerErrorFailure("Unable to apply circulation rules for unknown holding")));
    }

    String loanTypeId = item.determineLoanTypeForItem();
    String locationId = item.getLocationId();
    String materialTypeId = item.getMaterialTypeId();
    String patronGroupId = user.getPatronGroupId();

    CompletableFuture<Response> circulationRulesResponse = new CompletableFuture<>();

    log.info(
      "Applying circulation rules for material type: {}, patron group: {}, loan type: {}, location: {}",
      materialTypeId, patronGroupId, loanTypeId, locationId);

    circulationRulesClient.applyRules(loanTypeId, locationId, materialTypeId,
      patronGroupId, ResponseHandler.any(circulationRulesResponse));

    circulationRulesResponse.thenAcceptAsync(response -> {
      if (response.getStatusCode() == 404) {
        findLoanPolicyCompleted.complete(HttpResult.failed(
          new ServerErrorFailure("Unable to apply circulation rules")));
      } else if (response.getStatusCode() != 200) {
        findLoanPolicyCompleted.complete(HttpResult.failed(
          new ForwardOnFailure(response)));
      } else {
        String policyId = fetchPolicyId(response.getJson());
        findLoanPolicyCompleted.complete(succeeded(policyId));
      }
    });

    return findLoanPolicyCompleted;
  }

  protected abstract String getPolicyNotFoundErrorMessage(String policyId);

  protected abstract HttpResult<T> toPolicy(JsonObject representation);

  protected abstract String fetchPolicyId(JsonObject jsonObject);
}
