package org.folio.circulation.resources;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;

class ScheduledRequestAnonymizationProcessingResourceTest {
  private static final String ENDPOINT =
    "/circulation/scheduled-request-anonymize-processing";

  @Test
  void scheduledRequestAnonymizationResourceRegisters() {
    Vertx vertx = Vertx.vertx();

    try {
      Router router = Router.router(vertx);
      HttpClient client = vertx.createHttpClient();

      new ScheduledRequestAnonymizationProcessingResource(client)
        .register(router);

      assertTrue(
        router.getRoutes().stream()
          .anyMatch(route -> ENDPOINT.equals(route.getPath()))
      );
    }
    finally {
      vertx.close()
        .toCompletionStage()
        .toCompletableFuture()
        .join();
    }
  }

  @Test
  void scheduledRequestAnonymizationRespondsWithEmptyBodyImmediately()
    throws Exception {

    Vertx vertx = Vertx.vertx();

    try {
      Router router = Router.router(vertx);
      HttpClient client = vertx.createHttpClient();
      int port = NetworkUtils.nextFreePort();

      new ScheduledRequestAnonymizationProcessingResource(client)
        .register(router);

      var server = vertx.createHttpServer()
        .requestHandler(router)
        .listen(port)
        .toCompletionStage()
        .toCompletableFuture()
        .get(1, TimeUnit.SECONDS);

      try {
        var response = WebClient.create(vertx)
          .post(port, "localhost", ENDPOINT)
          .send()
          .toCompletionStage()
          .toCompletableFuture()
          .get(1, TimeUnit.SECONDS);

        assertEquals(200, response.statusCode());
        assertEquals(0, response.body() == null ? 0 : response.body().length());
      }
      finally {
        server.close()
          .toCompletionStage()
          .toCompletableFuture()
          .get(1, TimeUnit.SECONDS);
      }
    }
    finally {
      vertx.close()
        .toCompletionStage()
        .toCompletableFuture()
        .get(1, TimeUnit.SECONDS);
    }
  }
}
