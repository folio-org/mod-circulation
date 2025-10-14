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

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestAnonymizationResource extends Resource {

  public RequestAnonymizationResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/requests/anonymize", router);
    routeRegistration.create(this::anonymizeRequests);
  }

  private void anonymizeRequests(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    JsonObject body = routingContext.getBodyAsJson();

    if (body == null || !body.containsKey("requestIds")) {
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
      Result<JsonObject> failedResult = Result.failed(new ValidationErrorFailure(
        new ValidationError("requestIds array cannot be empty", "requestIds", null)));
      context.writeResultToHttpResponse(failedResult.map(JsonHttpResponse::ok));
      return;
    }

    // Chain the operations
    validateRequestsEligible(requestIds, clients)
      .thenCompose(r -> r.after(v -> anonymizeRequestsInStorage(requestIds, clients)))
      .thenApply(r -> r.map(v -> new JsonObject()
        .put("processed", requestIds.size())
        .put("anonymizedRequests", new JsonArray(requestIds))))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<Void>> validateRequestsEligible(
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
            return Result.failed(result.cause());
          }

          org.folio.circulation.domain.Request request = result.value();
          String status = request.getStatus().getValue();

          boolean isEligible = status.equals("Closed - Filled") ||
            status.equals("Closed - Cancelled") ||
            status.equals("Closed - Pickup expired") ||
            status.equals("Closed - Unfilled");

          if (!isEligible) {
            return Result.failed(new ValidationErrorFailure(
              new ValidationError(
                "Request " + request.getId() + " cannot be anonymized - status must be closed",
                "status", status)));
          }
        }
        return succeeded(null);
      });
  }

  private CompletableFuture<Result<Void>> anonymizeRequestsInStorage(
    List<String> requestIds, Clients clients) {

    JsonObject payload = new JsonObject()
      .put("requestIds", new JsonArray(requestIds));

    return clients.requestsStorage()
      .post(payload, "/request-storage/requests/anonymize")
      .thenApply(r -> r.map(response -> null));
  }
}
