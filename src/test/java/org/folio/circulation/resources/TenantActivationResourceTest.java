package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.folio.circulation.services.events.KafkaService;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.core.buffer.Buffer;

@ExtendWith(MockitoExtension.class)
class TenantActivationResourceTest {
  private static final String ENDPOINT = "/_/tenant";
  private static final String TENANT_ID = "test_tenant";
  private static final int TIMEOUT_SECONDS = 10;

  @Mock
  private KafkaService kafkaService;

  private Vertx vertx;
  private WebClient webClient;
  private HttpServer server;
  private int port;

  @BeforeEach
  void setUp() throws Exception {
    vertx = Vertx.vertx();
    webClient = WebClient.create(vertx);
    port = NetworkUtils.nextFreePort();

    Router router = Router.router(vertx);
    new TenantActivationResource(vertx.createHttpClient(), ignored -> kafkaService)
      .register(router);

    server = vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .toCompletionStage()
      .toCompletableFuture()
      .get(TIMEOUT_SECONDS, SECONDS);
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
        .get(TIMEOUT_SECONDS, SECONDS);
    }

    if (vertx != null) {
      vertx.close()
        .toCompletionStage()
        .toCompletableFuture()
        .get(TIMEOUT_SECONDS, SECONDS);
    }
  }

  @Test
  void tenantActivationFailsWhenKafkaTopicCreationFails() throws Exception {
    when(kafkaService.createCirculationTopics(TENANT_ID))
      .thenReturn(failedFuture(new RuntimeException("Kafka is unavailable")));

    HttpResponse<Buffer> response = webClient.post(port, "localhost", ENDPOINT)
      .putHeader(TENANT, TENANT_ID)
      .putHeader(OKAPI_URL, "http://localhost:" + port)
      .sendJsonObject(new JsonObject().put("id", "mod-circulation"))
      .toCompletionStage()
      .toCompletableFuture()
      .get(TIMEOUT_SECONDS, SECONDS);

    assertEquals(500, response.statusCode());
    verify(kafkaService).createCirculationTopics(TENANT_ID);
  }

  @Test
  void tenantPurgeFailsWhenKafkaTopicDeletionFails() throws Exception {
    when(kafkaService.deleteCirculationTopics(TENANT_ID))
      .thenReturn(failedFuture(new RuntimeException("Kafka is unavailable")));

    HttpResponse<Buffer> response = deleteTenant(true);

    assertEquals(500, response.statusCode());
    verify(kafkaService).deleteCirculationTopics(TENANT_ID);
  }

  @Test
  void tenantDeactivationDoesNotDeleteKafkaTopicsWithoutPurge() throws Exception {
    HttpResponse<Buffer> response = deleteTenant(false);

    assertEquals(204, response.statusCode());
    verifyNoInteractions(kafkaService);
  }

  private HttpResponse<Buffer> deleteTenant(boolean purge) throws Exception {
    return webClient.delete(port, "localhost", ENDPOINT)
      .putHeader(TENANT, TENANT_ID)
      .sendJsonObject(new JsonObject().put("purge", purge))
      .toCompletionStage()
      .toCompletableFuture()
      .get(TIMEOUT_SECONDS, SECONDS);
  }
}
