package org.folio.circulation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.folio.circulation.resources.ScheduledRequestAnonymizationProcessingResource;


import io.vertx.core.http.HttpClient;
import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.HttpResponse;


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
