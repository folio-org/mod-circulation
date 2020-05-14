package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.JsonHttpResponse.created;

import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.support.RouteRegistration;
import org.folio.util.pubsub.PubSubClientUtils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CirculationTenantAPI {
  private static final Logger logger = LoggerFactory.getLogger(CirculationTenantAPI.class);

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/_/tenant", router);
    routeRegistration.create(this::postTenant);
  }

  public void postTenant(RoutingContext routingContext) {
    Map<String, String> headers = routingContext.request().headers().entries().stream()
      .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));

    Vertx vertx = routingContext.vertx();

    vertx.executeBlocking(
      promise -> registerModuleToPubsub(headers, vertx).setHandler(promise::complete),
      result -> created(new JsonObject()).writeTo(routingContext.response())
    );
  }

  private Future<Void> registerModuleToPubsub(Map<String, String> headers, Vertx vertx) {
    Promise<Void> promise = Promise.promise();
    PubSubClientUtils.registerModule(new org.folio.rest.util.OkapiConnectionParams(headers, vertx))
      .whenComplete((registrationAr, throwable) -> {
        if (throwable == null) {
          logger.info("Module was successfully registered as publisher/subscriber in mod-pubsub");
          promise.complete();
        } else {
          logger.error("Error during module registration in mod-pubsub", throwable);
          promise.fail(throwable);
        }
      });
    return promise.future();
  }
}
