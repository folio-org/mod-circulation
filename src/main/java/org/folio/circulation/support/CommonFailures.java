package org.folio.circulation.support;

import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;

import java.util.function.Consumer;

public class CommonFailures {
  private CommonFailures() { }

  public static Consumer<String> reportFailureToClient(
    RoutingContext routingContext) {

    return f -> ServerErrorResponse.internalError(routingContext.response(), f);
  }

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
