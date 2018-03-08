package org.folio.circulation.support;

import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;

import io.vertx.ext.web.RoutingContext;

public class CommonFailures {
  private CommonFailures() { }

  public static void reportFailureToFetchInventoryRecords(
    RoutingContext routingContext,
    Exception cause) {

    ServerErrorResponse.internalError(routingContext.response(),
      String.format("Could not get inventory records related to request: %s", cause));
  }

  public static void reportInvalidOkapiUrlHeader(
    RoutingContext routingContext,
    String okapiLocation) {

    ServerErrorResponse.internalError(routingContext.response(),
      String.format("Invalid Okapi URL: %s", okapiLocation));
  }

  public static void reportItemRelatedValidationError(
    RoutingContext routingContext,
    String itemId,
    String reason) {

    JsonResponse.unprocessableEntity(routingContext.response(),
      reason, "itemId", itemId);
  }

  public static void reportProxyRelatedValidationError(
      RoutingContext routingContext,
      String proxyUserId,
      String reason) {

      JsonResponse.unprocessableEntity(routingContext.response(),
        reason, "proxyUserId", proxyUserId);
  }
}
