package org.folio.circulation.support;

import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;

public class CommonFailures {
  private CommonFailures() { }

  public static void reportFailureToFetchInventoryRecords(
    RoutingContext routingContext,
    Exception cause) {

    ServerErrorResponse.internalError(routingContext.response(),
      String.format("Could not get inventory records related to request: %s", cause));
  }

  public static void reportItemRelatedValidationError(
    RoutingContext routingContext,
    String itemId,
    String reason) {

    JsonResponse.unprocessableEntity(routingContext.response(),
      reason, "itemId", itemId);
  }

  public static Route withFailureHandler(Route route) {
    return route.failureHandler(CommonFailures::respondWithFailureReason);
  }

  public static void respondWithFailureReason(RoutingContext context) {
    Throwable failure = context.failure();

    if(failure != null) {
      if(StringUtils.isNotBlank(failure.getMessage())) {
        ServerErrorResponse.internalError(context.response(), failure.getMessage());
      }
      else {
        ServerErrorResponse.internalError(context.response(), failure.toString());
      }
    }
    else {
      ServerErrorResponse.internalError(context.response(), "Unknown failure occurred");
    }
  }
}
