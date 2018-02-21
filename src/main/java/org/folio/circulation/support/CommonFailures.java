package org.folio.circulation.support;

import io.vertx.ext.web.RoutingContext;
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

}
