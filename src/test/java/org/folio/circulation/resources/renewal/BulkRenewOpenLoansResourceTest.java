package org.folio.circulation.resources.renewal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class BulkRenewOpenLoansResourceTest {

  @Test
  void bulkRenewOpenLoansRouteIsRegistered() {
    Vertx vertx = Vertx.vertx();
    Router router = Router.router(vertx);
    HttpClient client = vertx.createHttpClient();

    new BulkRenewOpenLoansResource(client).register(router);

    assertTrue(router.getRoutes().stream()
      .anyMatch(route -> "/circulation/bulk-renew-open-loans".equals(route.getPath())));
  }

  @Test
  void bulkRenewOpenLoansReturnsNoContentToPostRequests(Vertx vertx,
    VertxTestContext testContext) {

    Router router = Router.router(vertx);
    HttpClient client = vertx.createHttpClient();

    new BulkRenewOpenLoansResource(client).register(router);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(0)
      .onComplete(testContext.succeeding(server ->
        WebClient.create(vertx)
          .post(server.actualPort(), "localhost", "/circulation/bulk-renew-open-loans")
          .send()
          .onComplete(testContext.succeeding(response -> {
            assertEquals(204, response.statusCode());

            server.close()
              .onComplete(testContext.succeeding(v -> testContext.completeNow()));
          }))));
  }

  @Test
  void bulkRenewOpenLoansReturnsBadRequestWhenJobIsNotAccepted(Vertx vertx,
    VertxTestContext testContext) {

    Router router = Router.router(vertx);
    HttpClient client = vertx.createHttpClient();
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();

    new BulkRenewOpenLoansResource(client, guard, new RejectingService(guard)).register(router);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(0)
      .onComplete(testContext.succeeding(server ->
        WebClient.create(vertx)
          .post(server.actualPort(), "localhost", "/circulation/bulk-renew-open-loans")
          .send()
          .onComplete(testContext.succeeding(response -> {
            assertEquals(400, response.statusCode());
            assertEquals("bulk renewal job already running", response.bodyAsString());

            server.close()
              .onComplete(testContext.succeeding(v -> testContext.completeNow()));
          }))));
  }

  @Test
  void bulkRenewalWebContextDetachesHeadersFromSourceMap() {
    Map<String, String> headers = new HashMap<>();
    headers.put("x-okapi-user-id", "user-1");

    BulkRenewalWebContext context = new BulkRenewalWebContext(headers);
    headers.put("x-okapi-user-id", "user-2");

    assertEquals("user-1", context.getUserId());
    assertThrows(UnsupportedOperationException.class,
      () -> context.getHeaders().put("another-header", "value"));
  }

  @Test
  void bulkRenewalWebContextFindsCanonicalOkapiUserHeader() {
    Map<String, String> headers = Map.of("X-Okapi-User-Id", "user-1");

    BulkRenewalWebContext context = new BulkRenewalWebContext(headers);

    assertEquals("user-1", context.getUserId());
  }

  @Test
  void bulkRenewOpenLoansResourceAllowsInjectingJobGuard() throws Exception {
    Vertx vertx = Vertx.vertx();
    HttpClient client = vertx.createHttpClient();
    BulkRenewalJobGuard jobGuard = new BulkRenewalJobGuard();

    Constructor<BulkRenewOpenLoansResource> constructor =
      BulkRenewOpenLoansResource.class.getDeclaredConstructor(HttpClient.class,
        BulkRenewalJobGuard.class);
    constructor.setAccessible(true);

    BulkRenewOpenLoansResource resource = constructor.newInstance(client, jobGuard);
    Field guardField = BulkRenewOpenLoansResource.class.getDeclaredField("jobGuard");
    guardField.setAccessible(true);

    assertSame(jobGuard, guardField.get(resource));
  }

  @Test
  void bulkRenewOpenLoansResourceCreatesDefaultServicePerInstance() throws Exception {
    Vertx vertx = Vertx.vertx();
    HttpClient client = vertx.createHttpClient();

    BulkRenewOpenLoansResource first = new BulkRenewOpenLoansResource(client);
    BulkRenewOpenLoansResource second = new BulkRenewOpenLoansResource(client);
    Field serviceField = BulkRenewOpenLoansResource.class.getDeclaredField("service");
    serviceField.setAccessible(true);

    assertNotSame(serviceField.get(first), serviceField.get(second));
  }

  private static final class RejectingService extends BulkRenewOpenLoansService {
    private RejectingService(BulkRenewalJobGuard guard) {
      super(guard, BulkRenewOpenLoansService::noOpRunner);
    }

    @Override
    public boolean trigger() {
      return false;
    }
  }
}
