package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.circulation.rules.Drools;
import org.folio.circulation.support.OkJsonHttpResult;

import static org.folio.circulation.support.http.server.ServerErrorResponse.internalError;

/**
 * The circulation rules engine calculates the loan policy based on
 * item type, loan type, patron type and shelving location.
 */
public class NoticeCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public NoticeCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client);
  }

  protected boolean invalidApplyParameters(HttpServerRequest request) {
    return
        invalidUuid(request, ITEM_TYPE_ID_NAME) ||
        invalidUuid(request, LOAN_TYPE_ID_NAME) ||
        invalidUuid(request, PATRON_TYPE_ID_NAME) ||
        invalidUuid(request, SHELVING_LOCATION_ID_NAME);
  }

  void apply(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    if (invalidApplyParameters(request)) {
      return;
    }
    drools(routingContext, drools -> {
      try {
        String itemTypeId = request.getParam(ITEM_TYPE_ID_NAME);
        String loanTypeId = request.getParam(LOAN_TYPE_ID_NAME);
        String patronGroupId = request.getParam(PATRON_TYPE_ID_NAME);
        String shelvingLocationId = request.getParam(SHELVING_LOCATION_ID_NAME);
        String loanPolicyId = drools.noticePolicy(itemTypeId, loanTypeId, patronGroupId, shelvingLocationId);
        JsonObject json = new JsonObject().put("noticePolicyId", loanPolicyId);

        new OkJsonHttpResult(json)
          .writeTo(routingContext.response());
      }
      catch (Exception e) {
        log.error("apply notice policy", e);
        internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
      }
    });
  }

  void applyAll(RoutingContext routingContext, Drools drools) {
    HttpServerRequest request = routingContext.request();
    if (invalidApplyParameters(request)) {
      return;
    }
    try {
      String itemTypeId = request.getParam(ITEM_TYPE_ID_NAME);
      String loanTypeId = request.getParam(LOAN_TYPE_ID_NAME);
      String patronGroupId = request.getParam(PATRON_TYPE_ID_NAME);
      String shelvingLocationId = request.getParam(SHELVING_LOCATION_ID_NAME);
      JsonArray matches = drools.noticePolicies(itemTypeId, loanTypeId, patronGroupId, shelvingLocationId);
      JsonObject json = new JsonObject().put("circulationRuleMatches", matches);

      new OkJsonHttpResult(json)
        .writeTo(routingContext.response());
    }
    catch (Exception e) {
      log.error("applyAll", e);
      internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
    }
  }
}
