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
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.AllowedServicePoint;
import org.folio.circulation.domain.AllowedServicePointsRequest;
import org.folio.circulation.domain.Request;
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

    Request.Operation operation = Optional.ofNullable(queryParams.get("operation"))
      .map(String::toUpperCase)
      .map(Request.Operation::valueOf)
      .orElse(null);

    AllowedServicePointsRequest request = new AllowedServicePointsRequest(operation,
      queryParams.get("requesterId"), queryParams.get("instanceId"), queryParams.get("itemId"),
      queryParams.get("requestId"));

    return validateAllowedServicePointsRequest(request);
  }

  private static Result<AllowedServicePointsRequest> validateAllowedServicePointsRequest(
    AllowedServicePointsRequest allowedServicePointsRequest) {

    log.debug("validateAllowedServicePointsRequest:: parameters allowedServicePointsRequest: {}",
      allowedServicePointsRequest);

    Request.Operation operation = allowedServicePointsRequest.getOperation();
    String requesterId = allowedServicePointsRequest.getRequesterId();
    String instanceId = allowedServicePointsRequest.getInstanceId();
    String itemId = allowedServicePointsRequest.getItemId();
    String requestId = allowedServicePointsRequest.getRequestId();

    List<String> errors = new ArrayList<>();

    // Checking UUID validity

    if (requesterId != null && !isUuid(requesterId)) {
      log.warn("Requester ID is not a valid UUID: {}", requesterId);
      errors.add(String.format("Requester ID is not a valid UUID: %s.", requesterId));
    }

    if (instanceId != null && !isUuid(instanceId)) {
      log.warn("Instance ID is not a valid UUID: {}", requesterId);
      errors.add(String.format("Instance ID is not a valid UUID: %s.", instanceId));
    }

    if (itemId != null && !isUuid(itemId)) {
      log.warn("Item ID is not a valid UUID: {}", itemId);
      errors.add(String.format("Item ID is not a valid UUID: %s.", itemId));
    }

    if (requestId != null && !isUuid(requestId)) {
      log.warn("Request ID is not a valid UUID: {}", requestId);
      errors.add(String.format("Request ID is not a valid UUID: %s.", requestId));
    }

    // Checking parameter combinations

    boolean allowedCombinationOfParametersDetected = false;

    if (operation == Request.Operation.CREATE && requesterId != null && instanceId != null &&
      itemId == null && requestId == null) {

      log.info("validateAllowedServicePointsRequest:: TLR request creation case");
      allowedCombinationOfParametersDetected = true;
    }

    if (operation == Request.Operation.CREATE && requesterId != null && instanceId == null &&
      itemId != null && requestId == null) {

      log.info("validateAllowedServicePointsRequest:: ILR request creation case");
      allowedCombinationOfParametersDetected = true;
    }

    if (operation == Request.Operation.REPLACE && requesterId == null && instanceId == null &&
      itemId == null && requestId != null) {

      log.info("validateAllowedServicePointsRequest:: request replacement case");
      allowedCombinationOfParametersDetected = true;
    }

    if (operation == Request.Operation.MOVE && requesterId == null && instanceId == null &&
      itemId != null && requestId != null) {

      log.info("validateAllowedServicePointsRequest:: request movement case");
      allowedCombinationOfParametersDetected = true;
    }

    if (!allowedCombinationOfParametersDetected) {
      String errorMessage = "Invalid combination of query parameters";
      errors.add(errorMessage);
    }

    if (!errors.isEmpty()) {
      String errorMessage = String.join(" ", errors);
      log.error("validateRequest:: allowed service points request failed: {}", errorMessage);
      return failed(new BadRequestFailure(errorMessage));
    }

    return succeeded(allowedServicePointsRequest);
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
