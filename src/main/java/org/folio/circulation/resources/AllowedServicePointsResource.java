package org.folio.circulation.resources;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.asJson;
import static org.folio.util.UuidUtil.isUuid;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.AllowedServicePoint;
import org.folio.circulation.domain.AllowedServicePointsRequest;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.services.AllowedServicePointsService;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class AllowedServicePointsResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public AllowedServicePointsResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    router.get("/circulation/requests/allowed-service-points")
      .handler(this::get);
  }

  private void get(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    ofAsync(routingContext)
      .thenApply(r -> r.next(AllowedServicePointsResource::buildRequest))
      .thenCompose(r -> r.after(new AllowedServicePointsService(clients)::getAllowedServicePoints))
      .thenApply(r -> r.map(AllowedServicePointsResource::toJson))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenAccept(context::writeResultToHttpResponse);
  }

  private static Result<AllowedServicePointsRequest> buildRequest(RoutingContext routingContext) {
    MultiMap queryParams = routingContext.queryParams();
    AllowedServicePointsRequest request = new AllowedServicePointsRequest(
      queryParams.get("requester"),
      queryParams.get("instance"),
      queryParams.get("item"));

    return validateRequest(request);
  }

  private static Result<AllowedServicePointsRequest> validateRequest(
    AllowedServicePointsRequest request) {

    log.debug("validateRequest:: parameters: request={}", request);

    List<String> errors = new ArrayList<>();
    String requesterId = request.getRequesterId();
    String instanceId = request.getInstanceId();
    String itemId = request.getItemId();

    if (requesterId == null) {
      errors.add("Request query parameters must contain 'requester'.");
    } else if (!isUuid(requesterId)) {
      errors.add(String.format("Requester ID is not a valid UUID: %s.", requesterId));
    }

    if (instanceId == null ^ itemId != null) {
      errors.add("Request query parameters must contain either 'instance' or 'item'.");
    }

    if (instanceId != null && !isUuid(instanceId)) {
      errors.add(String.format("Instance ID is not a valid UUID: %s.", instanceId));
    }

    if (itemId != null && !isUuid(itemId)) {
      errors.add(String.format("Item ID is not a valid UUID: %s.", itemId));
    }

    if (!errors.isEmpty()) {
      String errorMessage = String.join(" ", errors);
      log.error("validateRequest:: allowed service points request failed: {}", errorMessage);
      return failed(new BadRequestFailure(errorMessage));
    }

    return succeeded(request);
  }

  private static JsonObject toJson(Map<RequestType, Set<AllowedServicePoint>> allowedServicePoints) {
    log.debug("toJson:: parameters: allowedServicePoints={}", () -> asJson(allowedServicePoints));
    JsonObject response = new JsonObject();
    if (allowedServicePoints == null) {
      log.info("toJson:: allowedServicePoints is null");
      return response;
    }

    allowedServicePoints.forEach((key, value) -> response.put(key.getValue(),
      new JsonArray(value.stream().toList())));
    log.info("allowedServicePoints:: result={}", response);

    return response;
  }
}
