package org.folio.circulation.infrastructure.storage.requests;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.support.CirculationRulesClient;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class RequestPolicyRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CirculationRulesClient circulationRequestRulesClient;
  private final CollectionResourceClient requestPoliciesStorageClient;

  public RequestPolicyRepository(Clients clients) {
    this.circulationRequestRulesClient = clients.circulationRequestRules();
    this.requestPoliciesStorageClient = clients.requestPoliciesStorage();
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
      .thenComposeAsync(r -> r.after(this::lookupRequestPolicy))
      .thenApply(result -> result.map(RequestPolicy::from));
  }

  private CompletableFuture<Result<JsonObject>> lookupRequestPolicy(
    String requestPolicyId) {

    return SingleRecordFetcher.json(requestPoliciesStorageClient, "request policy",
      response -> failedDueToServerError(format(
        "Request policy %s could not be found, please check circulation rules", requestPolicyId)))
      .fetch(requestPolicyId);
  }

  private CompletableFuture<Result<String>> lookupRequestPolicyId(
    Item item, User user) {

    if (item.isNotFound()) {
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

    CompletableFuture<Result<Response>> circulationRulesResponse =
      circulationRequestRulesClient.applyRules(loanTypeId, locationId, materialTypeId,
      patronGroupId);

    return circulationRulesResponse
      .thenComposeAsync(r -> r.after(this::processRulesResponse));
  }

  private CompletableFuture<Result<String>> processRulesResponse(Response response) {
    final CompletableFuture<Result<String>> future = new CompletableFuture<>();

    if (response.getStatusCode() == 404) {
      future.complete(failedDueToServerError("Unable to find matching request rules"));
    } else if (response.getStatusCode() != 200) {
      future.complete(failed(new ForwardOnFailure(response)));
    } else {
      future.complete(succeeded(response.getJson().getString("requestPolicyId")));
    }

    return future;
  }
}
