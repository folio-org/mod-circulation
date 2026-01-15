package org.folio.circulation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Router;

class ScheduledRequestAnonymizationProcessingResourceTest {

  @Test
  void scheduledRequestAnonymizationResourceRegisters() {
    Vertx vertx = Vertx.vertx();
    Router router = Router.router(vertx);

    HttpClient client = vertx.createHttpClient();

    new ScheduledRequestAnonymizationProcessingResource(client)
      .register(router);
  }

  @Test
  void scheduledRequestAnonymizationRouteIsRegistered() {
    Vertx vertx = Vertx.vertx();
    Router router = Router.router(vertx);

    HttpClient client = vertx.createHttpClient();
    new ScheduledRequestAnonymizationProcessingResource(client)
      .register(router);

    boolean routeExists = router.getRoutes().stream()
      .anyMatch(route ->
        "/circulation/scheduled-request-anonymize-processing"
          .equals(route.getPath()));
    assertEquals(true, routeExists);
  }

}
