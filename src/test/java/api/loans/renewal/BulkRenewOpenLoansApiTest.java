package api.loans.renewal;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.folio.circulation.resources.renewal.BulkRenewOpenLoansResource;
import org.folio.circulation.resources.renewal.BulkRenewOpenLoansService;
import org.folio.circulation.resources.renewal.BulkRenewalJobGuard;
import org.folio.circulation.resources.renewal.BulkRenewalWebContext;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class BulkRenewOpenLoansApiTest {

  @Test
  void shouldReturnImmediatelyAndLaunchDetachedContextBackgroundWork(Vertx vertx,
    VertxTestContext testContext) throws Exception {

    Router router = Router.router(vertx);
    HttpClient client = vertx.createHttpClient();
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();
    CompletableFuture<Result<Void>> runningJob = new CompletableFuture<>();
    AtomicInteger launchCount = new AtomicInteger();
    AtomicReference<BulkRenewalWebContext> capturedContext = new AtomicReference<>();

    BulkRenewOpenLoansService service = new BulkRenewOpenLoansService(guard,
      (jobId, detachedContext) -> {
        launchCount.incrementAndGet();
        capturedContext.set(detachedContext);
        return runningJob;
      });

    resource(client, guard, service).register(router);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(0)
      .onComplete(testContext.succeeding(server -> WebClient.create(vertx)
        .post(server.actualPort(), "localhost", "/circulation/bulk-renew-open-loans")
        .putHeader("X-Okapi-User-Id", "trigger-user")
        .putHeader("X-Okapi-Tenant", "test-tenant")
        .putHeader("X-Okapi-Url", "http://okapi.example")
        .putHeader("X-Okapi-Token", "token")
        .send()
        .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(204, response.statusCode());
          assertEquals(1, launchCount.get());
          assertTrue(guard.isRunning());
          assertEquals("trigger-user", capturedContext.get().getUserId());
          assertEquals("test-tenant",
            capturedContext.get().getHeaders().get("x-okapi-tenant"));

          runningJob.complete(succeeded(null));

          server.close()
            .onComplete(testContext.succeeding(v -> testContext.completeNow()));
        })))));
  }

  @Test
  void shouldRejectRepeatedTriggerWhileJobIsStillRunningThroughRealGuardPath(Vertx vertx,
    VertxTestContext testContext) throws Exception {

    Router router = Router.router(vertx);
    HttpClient client = vertx.createHttpClient();
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();
    CompletableFuture<Result<Void>> runningJob = new CompletableFuture<>();
    AtomicInteger launchCount = new AtomicInteger();

    BulkRenewOpenLoansService service = new BulkRenewOpenLoansService(guard,
      (jobId, detachedContext) -> {
        launchCount.incrementAndGet();
        return runningJob;
      });

    resource(client, guard, service).register(router);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(0)
      .onComplete(testContext.succeeding(server -> WebClient.create(vertx)
        .post(server.actualPort(), "localhost", "/circulation/bulk-renew-open-loans")
        .putHeader("X-Okapi-User-Id", "trigger-user")
        .send()
        .onComplete(testContext.succeeding(firstResponse -> {
          assertEquals(204, firstResponse.statusCode());
          assertEquals(1, launchCount.get());
          assertTrue(guard.isRunning());

          WebClient.create(vertx)
            .post(server.actualPort(), "localhost", "/circulation/bulk-renew-open-loans")
            .putHeader("X-Okapi-User-Id", "trigger-user")
            .send()
            .onComplete(testContext.succeeding(secondResponse -> testContext.verify(() -> {
              assertEquals(400, secondResponse.statusCode());
              assertEquals("bulk renewal job already running", secondResponse.bodyAsString());
              assertEquals(1, launchCount.get());

              runningJob.complete(succeeded(null));

              server.close()
                .onComplete(testContext.succeeding(v -> testContext.completeNow()));
            })));
        }))));
  }

  private BulkRenewOpenLoansResource resource(HttpClient client, BulkRenewalJobGuard guard,
    BulkRenewOpenLoansService service) throws Exception {

    Constructor<BulkRenewOpenLoansResource> constructor = BulkRenewOpenLoansResource.class
      .getDeclaredConstructor(HttpClient.class, BulkRenewalJobGuard.class,
        BulkRenewOpenLoansService.class);
    constructor.setAccessible(true);
    return constructor.newInstance(client, guard, service);
  }
}
