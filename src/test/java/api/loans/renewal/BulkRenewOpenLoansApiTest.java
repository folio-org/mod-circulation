package api.loans.renewal;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.UUID;
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
import org.mockito.Mockito;

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

  @Test
  void shouldPersistLoanAndItemAfterSuccessfulRenewal(Vertx vertx,
    VertxTestContext testContext) throws Exception {

    Router router = Router.router(vertx);
    HttpClient httpClient = vertx.createHttpClient();
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();

    LoanRepository loanRepository = Mockito.mock(LoanRepository.class);
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);

    UUID loanId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    Loan loan = new LoanBuilder()
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

    CompletableFuture<Void> jobDone = new CompletableFuture<>();

    BulkRenewOpenLoansService.DetachedContextJobRunner runner = (jobId, detachedContext) -> {
      StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);
      RenewalContext ctx = RenewalContext.create(loan, new JsonObject(), "user-1",
        "perf-" + loanId, System.currentTimeMillis());

      return storeLoanAndItem.updateLoanAndItemInStorage(ctx)
        .thenApply(r -> {
          jobDone.complete(null);
          return Result.<Void>succeeded(null);
        });
    };

    BulkRenewOpenLoansService service = new BulkRenewOpenLoansService(guard, runner);

    resource(httpClient, guard, service).register(router);

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

  private BulkRenewOpenLoansResource resource(HttpClient client, BulkRenewalJobGuard guard,
    BulkRenewOpenLoansService service) throws Exception {

    Constructor<BulkRenewOpenLoansResource> constructor = BulkRenewOpenLoansResource.class
      .getDeclaredConstructor(HttpClient.class, BulkRenewalJobGuard.class,
        BulkRenewOpenLoansService.class);
    constructor.setAccessible(true);
    return constructor.newInstance(client, guard, service);
  }
}
