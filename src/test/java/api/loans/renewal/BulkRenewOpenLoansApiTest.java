package api.loans.renewal;

import static java.util.UUID.randomUUID;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.renewal.BulkRenewOpenLoansResource;
import org.folio.circulation.resources.renewal.BulkRenewOpenLoansService;
import org.folio.circulation.resources.renewal.BulkRenewalJobGuard;
import org.folio.circulation.resources.renewal.BulkRenewalWebContext;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import api.support.builders.LoanBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class BulkRenewOpenLoansApiTest {

  @Test
  void shouldReturnImmediatelyAndLaunchDetachedContextBackgroundWork(Vertx vertx,
    VertxTestContext testContext) throws Exception {

    var router = Router.router(vertx);
    var client = vertx.createHttpClient();
    var guard = new BulkRenewalJobGuard();
    var runningJob = new CompletableFuture<Result<Void>>();
    var launchCount = new AtomicInteger();
    var capturedContext = new AtomicReference<BulkRenewalWebContext>();

    var service = new BulkRenewOpenLoansService(guard,
      (jobId, detachedContext) -> {
        launchCount.incrementAndGet();
        capturedContext.set(detachedContext);
        return runningJob;
      });

    resource(client, service).register(router);

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

    var router = Router.router(vertx);
    var client = vertx.createHttpClient();
    var guard = new BulkRenewalJobGuard();
    var runningJob = new CompletableFuture<Result<Void>>();
    var launchCount = new AtomicInteger();

    var service = new BulkRenewOpenLoansService(guard,
      (jobId, detachedContext) -> {
        launchCount.incrementAndGet();
        return runningJob;
      });

    resource(client, service).register(router);

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

  @Test
  void shouldPersistLoanAndItemAfterSuccessfulRenewal(Vertx vertx,
    VertxTestContext testContext) throws Exception {

    var router = Router.router(vertx);
    var httpClient = vertx.createHttpClient();
    var guard = new BulkRenewalJobGuard();

    var loanRepository = mock(LoanRepository.class);
    var itemRepository = mock(ItemRepository.class);

    var loanId = randomUUID();
    var itemId = randomUUID();

    var loan = new LoanBuilder()
      .withId(loanId)
      .withItemId(itemId)
      .asDomainObject()
      .withItem(Item.from(new JsonObject()
        .put("id", itemId.toString())
        .put("status", new JsonObject().put("name", "Checked out"))));

    when(loanRepository.updateLoan(any(Loan.class))).thenReturn(
      CompletableFuture.completedFuture(Result.succeeded(loan)));
    when(itemRepository.updateItem(any())).thenReturn(
      CompletableFuture.completedFuture(Result.succeeded(loan.getItem())));

    var jobDone = new CompletableFuture<Void>();

    var runner = (BulkRenewOpenLoansService.DetachedContextJobRunner) (jobId, detachedContext) -> {
      var storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);
      var ctx = RenewalContext.create(loan, new JsonObject(), "user-1");

      return storeLoanAndItem.updateLoanAndItemInStorage(ctx)
        .thenApply(r -> {
          jobDone.complete(null);
          return Result.<Void>succeeded(null);
        });
    };

    var service = new BulkRenewOpenLoansService(guard, runner);

    resource(httpClient, service).register(router);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(0)
      .onComplete(testContext.succeeding(server ->
        WebClient.create(vertx)
          .post(server.actualPort(), "localhost", "/circulation/bulk-renew-open-loans")
          .putHeader("X-Okapi-User-Id", "trigger-user")
          .putHeader("X-Okapi-Tenant", "test-tenant")
          .putHeader("X-Okapi-Url", "http://okapi.example")
          .putHeader("X-Okapi-Token", "token")
          .send()
          .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(204, response.statusCode());

            jobDone.whenComplete((v, err) -> testContext.verify(() -> {
              verify(loanRepository).updateLoan(any(Loan.class));
              server.close().onComplete(testContext.succeeding(ignored ->
                testContext.completeNow()));
            }));
          })))));
  }

  private BulkRenewOpenLoansResource resource(HttpClient client,
    BulkRenewOpenLoansService service) throws Exception {

    var constructor = BulkRenewOpenLoansResource.class
      .getDeclaredConstructor(HttpClient.class, BulkRenewOpenLoansService.class);
    constructor.setAccessible(true);
    return constructor.newInstance(client, service);
  }
}
