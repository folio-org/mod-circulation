package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.CirculationRulesClient;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class RequestPolicyRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CirculationRulesClient circulationRequestRulesClient;
  private final CollectionResourceClient requestPoliciesStorageClient;

  public RequestPolicyRepository(Clients clients) {
    this.circulationRequestRulesClient = clients.circulationRequestRules();
    this.requestPoliciesStorageClient = clients.requestPoliciesStorage();
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> lookupRequestPolicy(
    RequestAndRelatedRecords relatedRecords) {

    Request request = relatedRecords.getRequest();

    return lookupRequestPolicy(request.getItem(), request.getRequester())
      .thenApply(result -> result.map(relatedRecords::withRequestPolicy));
  }

  private CompletableFuture<Result<RequestPolicy>> lookupRequestPolicy(
    Item item,
    User user) {

    return lookupRequestPolicyId(item, user)
      .thenComposeAsync(r -> r.after(this::lookupRequestPolicy))
      .thenApply(result -> result.map(this::toRequestPolicy));
  }

  private RequestPolicy toRequestPolicy(JsonObject representation) {
    return new RequestPolicy(representation);
  }


  private CompletableFuture<Result<JsonObject>> lookupRequestPolicy(
    String requestPolicyId) {

    return SingleRecordFetcher.json(requestPoliciesStorageClient, "request policy",
      response -> failed(new ServerErrorFailure(
        String.format("Request policy %s could not be found, please check circulation rules", requestPolicyId))))
      .fetch(requestPolicyId);
  }

  private CompletableFuture<Result<String>> lookupRequestPolicyId(
    Item item,
    User user) {

    CompletableFuture<Result<String>> findRequestPolicyCompleted
      = new CompletableFuture<>();

    if(item.isNotFound()) {
      return CompletableFuture.completedFuture(failed(
        new ServerErrorFailure("Unable to find matching request rules for unknown item")));
    }

    String materialTypeId = item.getMaterialTypeId();
    String patronGroupId = user.getPatronGroupId();
    String loanTypeId = item.determineLoanTypeForItem();
    String locationId = item.getLocationId();

    CompletableFuture<Response> circulationRulesResponse = new CompletableFuture<>();

    log.info(
      "Applying request rules for material type: {}, patron group: {}, loan type: {}, location: {}",
      materialTypeId, patronGroupId, loanTypeId, locationId);

    circulationRequestRulesClient.applyRules(loanTypeId, locationId, materialTypeId,
      patronGroupId, ResponseHandler.any(circulationRulesResponse));

    circulationRulesResponse.thenAcceptAsync(response -> {
      if (response.getStatusCode() == 404) {
        findRequestPolicyCompleted.complete(failed(
          new ServerErrorFailure("Unable to find matching request rules")));
      } else if (response.getStatusCode() != 200) {
        findRequestPolicyCompleted.complete(failed(
          new ForwardOnFailure(response)));
      } else {
        findRequestPolicyCompleted.complete(
          succeeded(response.getJson().getString("requestPolicyId")));
      }
    });

    return findRequestPolicyCompleted;
  }

}
