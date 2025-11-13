package org.folio.circulation.resources;

import static org.folio.circulation.support.results.Result.succeeded;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.logging.Logging;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestAnonymizationResource extends Resource {

  private static final java.lang.invoke.MethodHandles.Lookup LOOKUP =
    java.lang.invoke.MethodHandles.lookup();
  private static final org.apache.logging.log4j.Logger log =
    org.apache.logging.log4j.LogManager.getLogger(LOOKUP.lookupClass());
  public RequestAnonymizationResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/requests/anonymize", router);
    routeRegistration.create(this::anonymizeRequests);
  }

  void anonymizeRequests(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    JsonObject body = routingContext.getBodyAsJson();

    // Validate request body
    if (body == null || !body.containsKey("requestIds")) {
      log.warn("anonymizeRequests:: Request body missing requestIds");
      Result<JsonObject> failedResult = Result.failed(new ValidationErrorFailure(
        new ValidationError("requestIds array is required", "requestIds", null)));
      context.writeResultToHttpResponse(failedResult.map(JsonHttpResponse::ok));
      return;
    }

    List<String> requestIds = body.getJsonArray("requestIds")
      .stream()
      .map(Object::toString)
      .collect(Collectors.toList());

    if (requestIds.isEmpty()) {
      log.warn("anonymizeRequests:: requestIds array is empty");
      Result<JsonObject> failedResult = Result.failed(new ValidationErrorFailure(
        new ValidationError("requestIds array cannot be empty", "requestIds", null)));
      context.writeResultToHttpResponse(failedResult.map(JsonHttpResponse::ok));
      return;
    }

    // Get includeCirculationLogs parameter (default to true)
    boolean includeCirculationLogs = body.getBoolean("includeCirculationLogs", true);

    log.info("anonymizeRequests:: Processing {} requests, includeCirculationLogs={}",
      requestIds.size(), includeCirculationLogs);

    // Chain the operations
    validateRequestsEligible(requestIds, clients)
      .thenCompose(r -> r.after(v ->
        anonymizeRequestsInStorage(requestIds, includeCirculationLogs, clients)))
      .thenApply(r -> r.map(v -> {
        log.info("anonymizeRequests:: Successfully anonymized {} requests", requestIds.size());
        return new JsonObject()
          .put("processed", requestIds.size())
          .put("anonymizedRequests", new JsonArray(requestIds));
      }))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(result -> {
        if (result.failed()) {
          log.error("anonymizeRequests:: Failed to anonymize requests: {}",
            result.cause().toString());
        }
        context.writeResultToHttpResponse(result);
      });
  }

  CompletableFuture<Result<Void>> validateRequestsEligible(
    List<String> requestIds, Clients clients) {

    RequestRepository requestRepository = new RequestRepository(clients);

    List<CompletableFuture<Result<org.folio.circulation.domain.Request>>> futures =
      requestIds.stream()
        .map(requestRepository::getById)
        .collect(Collectors.toList());

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> {
        for (CompletableFuture<Result<org.folio.circulation.domain.Request>> future : futures) {
          Result<org.folio.circulation.domain.Request> result = future.join();

          if (result.failed()) {
            log.warn("validateRequestsEligible:: Failed to retrieve request: {}",
              result.cause().toString());
            return Result.failed(result.cause());
          }

          org.folio.circulation.domain.Request request = result.value();
          RequestStatus status = request.getStatus();

          // Use the RequestStatus enum for cleaner validation
          boolean isEligible = status == RequestStatus.CLOSED_FILLED ||
            status == RequestStatus.CLOSED_CANCELLED ||
            status == RequestStatus.CLOSED_PICKUP_EXPIRED ||
            status == RequestStatus.CLOSED_UNFILLED;

          if (!isEligible) {
            log.warn("validateRequestsEligible:: Request {} has ineligible status: {}",
              request.getId(), status.getValue());
            return Result.failed(new ValidationErrorFailure(
              new ValidationError(
                "Request " + request.getId() + " cannot be anonymized - status must be closed",
                "status", status.getValue())));
          }

          // Optional: Check if already anonymized
          if (request.getRequesterId() == null || request.getRequesterId().isEmpty()) {
            log.warn("validateRequestsEligible:: Request {} appears to be already anonymized",
              request.getId());
            return Result.failed(new ValidationErrorFailure(
              new ValidationError(
                "Request " + request.getId() + " appears to be already anonymized",
                "requesterId", null)));
          }
        }

        log.info("validateRequestsEligible:: All {} requests are eligible for anonymization",
          requestIds.size());
        return succeeded(null);
      });
  }

  CompletableFuture<Result<Void>> anonymizeRequestsInStorage(
    List<String> requestIds, boolean includeCirculationLogs, Clients clients) {

    JsonObject payload = new JsonObject()
      .put("requestIds", new JsonArray(requestIds))
      .put("includeCirculationLogs", includeCirculationLogs);

    log.info("anonymizeRequestsInStorage:: Sending anonymization request to storage layer");

    return clients.requestsStorage()
      .post(payload, "/request-storage/requests/anonymize")
      .thenApply(r -> {
        if (r.succeeded()) {
          log.info("anonymizeRequestsInStorage:: Storage layer successfully processed requests");
        } else {
          log.error("anonymizeRequestsInStorage:: Storage layer failed: {}",
            r.cause().toString());
        }
        return r.map(response -> null);
      });
  }
}
