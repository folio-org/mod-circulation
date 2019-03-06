package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.ServerErrorResponse.internalError;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.circulation.rules.Drools;
import org.folio.circulation.support.OkJsonHttpResult;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * The circulation rules engine calculates the request policy based on
 * item type, request type, patron type and shelving location.
 */
public class RequestCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  private static final String REQUEST_TYPE_ID_NAME = "request_type_id";

  public RequestCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client);
  }

  protected boolean invalidApplyParameters(HttpServerRequest request) {
    return
        invalidUuid(request, ITEM_TYPE_ID_NAME) ||
        invalidUuid(request, REQUEST_TYPE_ID_NAME) ||
        invalidUuid(request, PATRON_TYPE_ID_NAME) ||
        invalidUuid(request, SHELVING_LOCATION_ID_NAME);
  }

  void applyAll(RoutingContext routingContext, Drools drools) {
    HttpServerRequest request = routingContext.request();
    if (invalidApplyParameters(request)) {
      return;
    }
    try {
      String itemTypeId = request.getParam(ITEM_TYPE_ID_NAME);
      String requestTypeId = request.getParam(REQUEST_TYPE_ID_NAME);
      String patronGroupId = request.getParam(PATRON_TYPE_ID_NAME);
      String shelvingLocationId = request.getParam(SHELVING_LOCATION_ID_NAME);
      JsonArray matches = drools.requestPolicies(itemTypeId, requestTypeId, patronGroupId, shelvingLocationId);
      JsonObject json = new JsonObject().put("circulationRuleMatches", matches);

      new OkJsonHttpResult(json)
        .writeTo(routingContext.response());
    }
    catch (Exception e) {
      log.error("applyAll", e);
      internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
    }
  }

  void apply(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    if (invalidApplyParameters(request)) {
      return;
    }
    drools(routingContext, drools -> {
      try {
        String itemTypeId = request.getParam(ITEM_TYPE_ID_NAME);
        String requestTypeId = request.getParam(REQUEST_TYPE_ID_NAME);
        String patronGroupId = request.getParam(PATRON_TYPE_ID_NAME);
        String shelvingLocationId = request.getParam(SHELVING_LOCATION_ID_NAME);
        String requestPolicyId = drools.requestPolicy(itemTypeId, requestTypeId, patronGroupId, shelvingLocationId);
        JsonObject json = new JsonObject().put("requestPolicyId", requestPolicyId);

        new OkJsonHttpResult(json)
          .writeTo(routingContext.response());
      }
      catch (Exception e) {
        log.error("apply request policy", e);
        internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
      }
    });
  }
}
