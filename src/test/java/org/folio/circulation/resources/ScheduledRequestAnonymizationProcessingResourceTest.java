package org.folio.circulation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;

class ScheduledRequestAnonymizationProcessingResourceTest {
  private static final String ENDPOINT =
    "/circulation/scheduled-request-anonymize-processing";
  private static final int TIMEOUT = 1;

  private Vertx vertx;
  private Router router;
  private WebClient webClient;
  private HttpServer server;
  private int port;

  @BeforeEach
  void setUp() throws Exception {
    vertx = Vertx.vertx();
    router = Router.router(vertx);
    HttpClient client = vertx.createHttpClient();
    webClient = WebClient.create(vertx);
    port = NetworkUtils.nextFreePort();

    new ScheduledRequestAnonymizationProcessingResource(client)
      .register(router);

    server = vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .toCompletionStage()
      .toCompletableFuture()
      .get(TIMEOUT, TimeUnit.SECONDS);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (webClient != null) {
      webClient.close();
    }

    if (server != null) {
      server.close()
        .toCompletionStage()
        .toCompletableFuture()
        .get(TIMEOUT, TimeUnit.SECONDS);
    }

    if (vertx != null) {
      vertx.close()
        .toCompletionStage()
        .toCompletableFuture()
        .get(TIMEOUT, TimeUnit.SECONDS);
    }
  }

//  @Test
//  void scheduledRequestAnonymizationResourceRegisters() {
//    assertTrue(router.getRoutes().stream()
//      .anyMatch(route -> ENDPOINT.equals(route.getPath())));
//  }
//
//  @Test
//  void scheduledRequestAnonymizationRespondsWithEmptyBodyImmediately()
//    throws Exception {
//
//    var response = webClient
//      .post(port, "localhost", ENDPOINT)
//      .send()
//      .toCompletionStage()
//      .toCompletableFuture()
//      .get(TIMEOUT, TimeUnit.SECONDS);
//
//    assertEquals(200, response.statusCode());
//    assertEquals(0, response.body() == null ? 0 : response.body().length());
//  }
}
