package org.folio.circulation.resources.renewal;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.resources.renewal.BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.ZonedDateTime;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.folio.circulation.domain.AutomatedPatronBlocks;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.services.EventPublishingService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.fetching.GetManyRecordsRepository;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import api.support.builders.LoanBuilder;
import api.support.builders.OverdueFinePolicyWithReminderFeesBuilder;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class BulkRenewOpenLoansServiceTest {
  private TestAppender appender;
  private org.apache.logging.log4j.core.Logger coreLogger;

  @BeforeEach
  void setUp() {
    appender = new TestAppender();
    appender.start();

    coreLogger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(
      BulkRenewOpenLoansService.class);
    coreLogger.addAppender(appender);
    coreLogger.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() {
    if (coreLogger != null && appender != null) {
      coreLogger.removeAppender(appender);
    }

    if (appender != null) {
      appender.stop();
    }
  }

  @Test
  void shouldRejectStartWhenJobAlreadyRunning() {
    var guard = new BulkRenewalJobGuard();
    var runningJob = new CompletableFuture<Result<Void>>();
    var invocations = new AtomicInteger();

    var service = BulkRenewOpenLoansService.forJobRunner(guard, jobId -> {
      invocations.incrementAndGet();
      return runningJob;
    });

    assertTrue(service.trigger());
    assertFalse(service.trigger());
    assertEquals(1, invocations.get());
    assertTrue(appender.hasMessageContaining("bulk renewal job already running"));

    runningJob.complete(Result.succeeded(null));
  }

  @Test
  void shouldAlwaysReleaseGuardWhenBackgroundJobFinishes() throws InterruptedException {
    var finished = new CountDownLatch(1);
    var guard = trackingGuard(finished);
    var runningJob = new CompletableFuture<Result<Void>>();

    var service = BulkRenewOpenLoansService.forJobRunner(guard,
      jobId -> runningJob);

    assertTrue(service.trigger());
    assertTrue(guard.isRunning());

    runningJob.completeExceptionally(new RuntimeException("boom"));

    assertTrue(finished.await(5, SECONDS));
    assertFalse(guard.isRunning());
  }

  @Test
  void shouldReleaseGuardAndLogErrorWhenRunnerReturnsSynchronousException()
      throws InterruptedException {

    // Arrange
    var finished = new CountDownLatch(1);
    var guard = trackingGuard(finished);
    var thrown = new RuntimeException("sync boom");

    var service = BulkRenewOpenLoansService.forJobRunner(guard,
      jobId -> { throw thrown; });

    // Act
    assertTrue(service.trigger());
    assertTrue(finished.await(5, SECONDS));

    // Assert — guard released, failure logged as a failed Result
    assertFalse(guard.isRunning());
    assertTrue(appender.hasMessageContaining("bulk renewal job failed"));
    assertTrue(appender.hasMessageContaining("bulk renewal job finished"));
  }

  @Test
  void shouldReleaseGuardAndLogErrorWhenRunnerReturnsFailedResult()
      throws InterruptedException {

    // Arrange
    var finished = new CountDownLatch(1);
    var guard = trackingGuard(finished);

    var service = BulkRenewOpenLoansService.forJobRunner(guard,
      jobId -> completedFuture(failedDueToServerError("downstream failure")));

    // Act
    assertTrue(service.trigger());
    assertTrue(finished.await(5, SECONDS));

    // Assert — guard released, failure cause logged
    assertFalse(guard.isRunning());
    assertTrue(appender.hasMessageContaining("bulk renewal job failed"));
    assertTrue(appender.hasMessageContaining("bulk renewal job finished"));
  }

  @Test
  void shouldLogJobFinishedOnSuccessfulCompletion() throws InterruptedException {
    var finished = new CountDownLatch(1);
    var guard = trackingGuard(finished);

    var service = BulkRenewOpenLoansService.forJobRunner(guard,
      jobId -> CompletableFuture.completedFuture(Result.succeeded(null)));

    assertTrue(service.trigger());
    assertTrue(finished.await(5, SECONDS));
    assertTrue(appender.hasMessageContaining("bulk renewal job started"));
    assertTrue(appender.hasMessageContaining("bulk renewal job finished"));
  }

  @Test
  void shouldProcessOpenLoansPageByPageSequentially() throws InterruptedException {
    var finished = new CountDownLatch(1);
    var guard = trackingGuard(finished);
    var repository = new RecordingOpenLoanPageRepository(
      page(loans(500)),
      page(loan(3)));
    var processedPages = new ArrayList<Integer>();

    var service = new BulkRenewOpenLoansService(guard,
      (jobId, detachedCtx) -> new BulkRenewOpenLoansService.RealJobComponents(
        repository,
        exactMatch("status.name", "Open"),
        (records, triggeringUserId, jId, pageNumber) -> {
          assertEquals("trigger-user", triggeringUserId);
          processedPages.add(pageNumber);

          return completedFuture(succeeded(new BulkRenewalPageContext(
            List.copyOf(records.getRecords()),
            Map.of(),
            null,
            triggeringUserId,
            List.of(),
            Map.of())));
        },
        pageContext -> completedFuture(succeeded(null)),
        new BulkRenewalWebContext(Map.of("x-okapi-user-id", "trigger-user")))
        .run(jobId));

    assertTrue(service.trigger());
    assertTrue(finished.await(5, SECONDS));

    assertEquals(List.of(1, 2), processedPages);
    assertEquals(List.of(0, 500), repository.offsets().stream()
      .map(Offset::getOffset)
      .toList());
    assertTrue(appender.hasMessageContaining("page start"));
    assertTrue(appender.hasMessageContaining("page finish"));
  }

  @Test
  void shouldReleasePageReferencesAfterProcessing() {
    BulkRenewalPageContext releasedContext = BulkRenewOpenLoansService.releasePageReferences(
      new BulkRenewalPageContext(
        List.of(loan(1)),
        Map.of(),
        null,
        "trigger-user",
        List.of(successfulRenewalContext(loan(1), "trigger-user")),
        Map.of("loan-1", new TestFailure("failed"))));

    assertTrue(releasedContext.loans().isEmpty());
    assertTrue(releasedContext.successfulRenewalContexts().isEmpty());
    assertTrue(releasedContext.failedRenewalsByLoanId().isEmpty());
    assertEquals("trigger-user", releasedContext.triggeringUserId());
  }

  @Test
  void shouldRejectTriggerWhenJobAlreadyRunningInSameInstance() throws InterruptedException {
    var finished = new CountDownLatch(1);
    var guard = trackingGuard(finished);
    var completion = new CompletableFuture<Result<Void>>();

    var service = new BulkRenewOpenLoansService(guard,
      (jobId, detachedCtx) -> new BulkRenewOpenLoansService.RealJobComponents(
        new RecordingOpenLoanPageRepository(page(loan(1))),
        exactMatch("status.name", "Open"),
        (records, triggeringUserId, jId, pageNumber) -> completedFuture(succeeded(
          new BulkRenewalPageContext(List.copyOf(records.getRecords()), Map.of(), null,
            triggeringUserId, List.of(), Map.of()))),
        pageContext -> completion,
        new BulkRenewalWebContext(Map.of("x-okapi-user-id", "trigger-user")))
        .run(jobId));

    assertTrue(service.trigger());
    assertFalse(service.trigger());

    completion.complete(succeeded(null));

    assertTrue(finished.await(5, SECONDS));
  }

  @Test
  void shouldUseChunkAwareRealRunnerFetchSeams() {
    var itemRepository = mock(ItemRepository.class);
    var userRepository = mock(UserRepository.class);
    var requestRepository = mock(RequestRepository.class);
    var requestCqlChunkSize = 50;
    var ids = List.of("a", "b");
    var loan = loanWithItemAndUser(1);
    var itemId = loan.getItemId();

    when(itemRepository.fetchFor(ids, ITEM_ID_CHUNK_SIZE))
      .thenReturn(completedFuture(succeeded(MultipleRecords.empty())));
    when(userRepository.getUsersForUserIds(ids, ITEM_ID_CHUNK_SIZE))
      .thenReturn(completedFuture(succeeded(Map.of())));
    when(requestRepository.findOpenRequestsByItemIds(List.of(itemId), requestCqlChunkSize))
      .thenReturn(completedFuture(succeeded(MultipleRecords.empty())));
    when(requestRepository.findOpenRequestsByInstanceIds(List.of("instance-1"),
      requestCqlChunkSize))
      .thenReturn(completedFuture(succeeded(MultipleRecords.empty())));

    BulkRenewOpenLoansService.RealRunnerFactories.itemFetcher(itemRepository).apply(ids).join();
    BulkRenewOpenLoansService.RealRunnerFactories.userFetcher(userRepository).apply(ids).join();
    BulkRenewOpenLoansService.RealRunnerFactories.requestQueueLookup(requestRepository)
      .lookupByLoanId(List.of(loan), TlrSettingsConfiguration.defaultSettings()).join();
    BulkRenewOpenLoansService.RealRunnerFactories.requestQueueLookup(requestRepository)
      .lookupByLoanId(List.of(loan), enabledTlrSettings()).join();

    verify(itemRepository).fetchFor(ids, ITEM_ID_CHUNK_SIZE);
    verify(itemRepository, never()).fetchFor(ids);
    verify(userRepository).getUsersForUserIds(ids, ITEM_ID_CHUNK_SIZE);
    verify(userRepository, never()).getUsersForUserIds(ids);
    verify(requestRepository).findOpenRequestsByItemIds(List.of(itemId), requestCqlChunkSize);
    verify(requestRepository, never()).findOpenRequestsByItemIds(List.of(itemId));
    verify(requestRepository).findOpenRequestsByInstanceIds(List.of("instance-1"),
      requestCqlChunkSize);
    verify(requestRepository, never()).findOpenRequestsByInstanceIds(List.of("instance-1"));
  }

  @Test
  void shouldUseFullLoanCompatiblePolicyResolver() {
    var loanPolicyRepository = mock(LoanPolicyRepository.class);
    var cachedDependencies = new BulkRenewalCachedDependencies(
      () -> completedFuture(succeeded(TlrSettingsConfiguration.defaultSettings())),
      () -> completedFuture(succeeded(java.time.ZoneId.of("UTC"))));
    var loan = loanWithItemAndUser(1);
    var loanPolicy = mock(LoanPolicy.class);
    var match = new CirculationRuleMatch("policy-1",
      new AppliedRuleConditions(true, true, true));

    when(loanPolicyRepository.lookupPolicyForBulkRenewal(loan, cachedDependencies))
      .thenReturn(completedFuture(succeeded(match)));
    when(loanPolicyRepository.lookupPolicy("policy-1", match.getAppliedRuleConditions()))
      .thenReturn(completedFuture(succeeded(loanPolicy)));

    var result = BulkRenewOpenLoansService.RealRunnerFactories.loanPolicyResolver(
      cachedDependencies, loanPolicyRepository).apply(loan).join();

    assertTrue(result.succeeded());
    assertEquals(loanPolicy, result.value());
    verify(loanPolicyRepository).lookupPolicyForBulkRenewal(loan, cachedDependencies);
    verify(loanPolicyRepository, never()).lookupPolicyId(loan);
  }

  @Test
  void shouldBlockPreparedRenewalWhenReminderFeesDisallowRenewal() {
    var now = ZonedDateTime.now();
    var loan = loanWithItemAndUser(1)
      .withOverdueFinePolicy(disallowRenewalWithReminderFeesPolicy())
      .withRemindersLastFeeBilled(1, now);
    var context = successfulRenewalContext(loan, "trigger-user");
    var errorHandler = new OverridingErrorHandler(null);
    var renewalLogicCalls = new AtomicInteger();

    var result = BulkRenewOpenLoansService.renewPreparedLoan(
      context,
      errorHandler,
      overdueFineContext -> completedFuture(succeeded(overdueFineContext)),
      (renewalContext, handler) -> {
        renewalLogicCalls.incrementAndGet();
        return completedFuture(succeeded(renewalContext));
      }).join();

    assertTrue(result.failed());
    assertEquals(0, renewalLogicCalls.get());

    var failure = (ValidationErrorFailure) result.cause();
    assertEquals("Renewals not allowed for loans with reminders.",
      failure.getErrors().stream().findFirst().orElseThrow().getMessage());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldEnforceAutomatedPatronBlockValidationForBulkRenewal() throws Exception {
    var clients = mock(Clients.class);
    var userManualBlocksClient = mock(CollectionResourceClient.class);
    var automatedPatronBlocksRepository = mock(AutomatedPatronBlocksRepository.class);
    var userId = randomUUID();
    var loan = new LoanBuilder()
      .withId(randomUUID())
      .withUserId(userId)
      .withItemId(randomUUID())
      .asDomainObject();
    var context = RenewalContext.create(loan, new JsonObject(), "trigger-user")
      .withRequestQueue(new RequestQueue(List.of()));

    when(clients.userManualBlocksStorageClient()).thenReturn(userManualBlocksClient);
    when(userManualBlocksClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(completedFuture(succeeded(emptyResponse("manualblocks"))));
    // Patron has an active automated block on renewals
    when(automatedPatronBlocksRepository.findByUserId(userId.toString()))
      .thenReturn(completedFuture(succeeded(new AutomatedPatronBlocks())));

    var runnerClass = Class.forName(
      "org.folio.circulation.resources.renewal.BulkRenewOpenLoansService$RealBackgroundJobRunner");
    var constructor = runnerClass.getDeclaredConstructor(HttpClient.class,
      BulkRenewalWebContext.class);
    constructor.setAccessible(true);
    var runner = constructor.newInstance(mock(HttpClient.class),
      new BulkRenewalWebContext(Map.of(), mock(Context.class)));
    var applyPatronBlockValidations = runnerClass.getDeclaredMethod(
      "applyPatronBlockValidations", Clients.class, OkapiPermissions.class,
      AutomatedPatronBlocksRepository.class, RenewalContext.class,
      CirculationErrorHandler.class);
    applyPatronBlockValidations.setAccessible(true);

    var validation =
      (CompletableFuture<Result<RenewalContext>>) applyPatronBlockValidations.invoke(runner,
        clients, OkapiPermissions.empty(), automatedPatronBlocksRepository, context,
        new OverridingErrorHandler(null));

    assertTrue(validation.join().succeeded());
    verify(userManualBlocksClient).getMany(any(CqlQuery.class), any(PageLimit.class));
    // Automated patron block lookup IS now called (no longer skipped)
    verify(automatedPatronBlocksRepository).findByUserId(userId.toString());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldFailRenewalWhenPatronHasAutomatedRenewalBlock() throws Exception {
    var clients = mock(Clients.class);
    var userManualBlocksClient = mock(CollectionResourceClient.class);
    var automatedPatronBlocksRepository = mock(AutomatedPatronBlocksRepository.class);
    var userId = randomUUID();
    var loan = new LoanBuilder()
        .withId(randomUUID())
        .withUserId(userId)
        .withItemId(randomUUID())
        .asDomainObject();
    var context = RenewalContext.create(loan, new JsonObject(), "trigger-user")
      .withRequestQueue(new RequestQueue(List.of()));

    // No manual blocks
    when(clients.userManualBlocksStorageClient()).thenReturn(userManualBlocksClient);
    when(userManualBlocksClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(completedFuture(succeeded(emptyResponse("manualblocks"))));
    // Patron has an active automated block with blockRenewals=true
    var blockJson = new JsonObject()
      .put("blockRenewals", true)
      .put("message", "Patron has reached max items charged out");
    var blocksResponse = new JsonObject()
      .put("automatedPatronBlocks", new JsonArray().add(blockJson))
      .put("totalRecords", 1);
    when(automatedPatronBlocksRepository.findByUserId(userId.toString()))
      .thenReturn(completedFuture(succeeded(AutomatedPatronBlocks.from(blocksResponse))));

    var runnerClass = Class.forName(
      "org.folio.circulation.resources.renewal.BulkRenewOpenLoansService$RealBackgroundJobRunner");
    var constructor = runnerClass.getDeclaredConstructor(HttpClient.class,
      BulkRenewalWebContext.class);
    constructor.setAccessible(true);
    var runner = constructor.newInstance(mock(HttpClient.class),
      new BulkRenewalWebContext(Map.of(),
        mock(Context.class)));
    var applyPatronBlockValidations = runnerClass.getDeclaredMethod(
      "applyPatronBlockValidations", Clients.class, OkapiPermissions.class,
      AutomatedPatronBlocksRepository.class, RenewalContext.class,
      CirculationErrorHandler.class);
    applyPatronBlockValidations.setAccessible(true);

    var errorHandler = new OverridingErrorHandler(null);
    var validation = (CompletableFuture<Result<RenewalContext>>) applyPatronBlockValidations.invoke(
      runner, clients, OkapiPermissions.empty(),
      automatedPatronBlocksRepository, context, errorHandler);
    validation.join();

    // The error handler captures the block; the result is succeeded but errorHandler has the error
    assertTrue(errorHandler.hasAny(CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY));
    verify(automatedPatronBlocksRepository).findByUserId(userId.toString());
  }

  @Test
  void shouldTriggerServiceFromResourceAndReturnNoContentImmediately(Vertx vertx,
    VertxTestContext testContext) {

    var router = Router.router(vertx);
    var client = vertx.createHttpClient();
    var guard = new BulkRenewalJobGuard();
    var service = new RecordingBulkRenewOpenLoansService(guard);

    new BulkRenewOpenLoansResource(client, service).register(router);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(0)
      .onComplete(testContext.succeeding(server ->
        WebClient.create(vertx)
          .post(server.actualPort(), "localhost", "/circulation/bulk-renew-open-loans")
          .send()
          .onComplete(testContext.succeeding(response -> {
            assertEquals(204, response.statusCode());
            assertTrue(service.wasTriggered());

            server.close()
              .onComplete(testContext.succeeding(v -> testContext.completeNow()));
          }))));
  }

  @Test
  void shouldCallUpdateLoanAndItemInStorageForEachSuccessfulRenewal()
      throws InterruptedException {

    var loanRepository = mock(LoanRepository.class);
    var itemRepository = mock(ItemRepository.class);

    var loan1 = minimalLoanWithItem(randomUUID(), randomUUID());
    var loan2 = minimalLoanWithItem(randomUUID(), randomUUID());

    when(loanRepository.updateLoan(loan1)).thenReturn(completedFuture(succeeded(loan1)));
    when(loanRepository.updateLoan(loan2)).thenReturn(completedFuture(succeeded(loan2)));
    when(itemRepository.updateItem(any())).thenReturn(completedFuture(succeeded(loan1.getItem())));

    var ctx1 = RenewalContext.create(loan1, new io.vertx.core.json.JsonObject(), "user-1");
    var ctx2 = RenewalContext.create(loan2, new io.vertx.core.json.JsonObject(), "user-1");

    var pageContext = new BulkRenewalPageContext(
      List.of(loan1, loan2), Map.of(), null,
      "user-1", List.of(ctx1, ctx2), Map.of());

    var storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);

    var latch = new CountDownLatch(1);
    var handler = (BulkRenewOpenLoansService.PageCompletionHandler) pc ->
      storeLoanAndItem.updateLoanAndItemInStorage(pc.successfulRenewalContexts().get(0).getLoan())
        .thenCompose(r -> storeLoanAndItem.updateLoanAndItemInStorage(
          pc.successfulRenewalContexts().get(1).getLoan()))
        .thenApply(r -> { latch.countDown(); return Result.<Void>succeeded(null); });

    handler.complete(pageContext).join();
    latch.await(5, SECONDS);

    verify(loanRepository).updateLoan(loan1);
    verify(loanRepository).updateLoan(loan2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void processSuccessfulRenewals_emptyPageContext_returnsSucceededWithoutCallingStorage()
      throws Exception {

    // Arrange — build a RealBackgroundJobRunner via reflection (same pattern as patron-block tests)
    var runnerClass = Class.forName(
      "org.folio.circulation.resources.renewal.BulkRenewOpenLoansService$RealBackgroundJobRunner");
    var constructor = runnerClass.getDeclaredConstructor(
      HttpClient.class, BulkRenewalWebContext.class);
    constructor.setAccessible(true);
    var runner = constructor.newInstance(
      mock(HttpClient.class),
      new BulkRenewalWebContext(Map.of(), mock(Context.class)));

    var processSuccessfulRenewals = runnerClass.getDeclaredMethod(
      "processSuccessfulRenewals",
      Clients.class, LoanRepository.class, ItemRepository.class,
      OverdueFinePolicyRepository.class, BulkRenewalPageContext.class);
    processSuccessfulRenewals.setAccessible(true);

    var loanRepository = mock(LoanRepository.class);
    var itemRepository = mock(ItemRepository.class);
    var overdueFinePolicyRepository = mock(OverdueFinePolicyRepository.class);

    // Empty page context — no successful renewals
    var emptyPageContext = new BulkRenewalPageContext(
      List.of(), Map.of(), null, "user-1", List.of(), Map.of());

    // Act
    var future =
      (CompletableFuture<Result<Void>>) processSuccessfulRenewals.invoke(
        runner,
        mock(Clients.class),
        loanRepository, itemRepository, overdueFinePolicyRepository,
        emptyPageContext);
    var result = future.join();

    // Assert — succeeded with no storage interactions
    assertTrue(result.succeeded());
    verify(loanRepository, never()).updateLoan(any(Loan.class));
    verify(itemRepository, never()).updateItem(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void processSuccessfulRenewals_twoSuccessfulContexts_persistsEachLoanAndItem()
      throws Exception {

    // Arrange — build RealBackgroundJobRunner via reflection
    var runnerClass = Class.forName(
      "org.folio.circulation.resources.renewal.BulkRenewOpenLoansService$RealBackgroundJobRunner");
    var constructor = runnerClass.getDeclaredConstructor(
      HttpClient.class, BulkRenewalWebContext.class);
    constructor.setAccessible(true);
    var runner = constructor.newInstance(
      mock(HttpClient.class),
      new BulkRenewalWebContext(Map.of(), mock(Context.class)));

    var processSuccessfulRenewals = runnerClass.getDeclaredMethod(
      "processSuccessfulRenewals",
      Clients.class, LoanRepository.class, ItemRepository.class,
      OverdueFinePolicyRepository.class, BulkRenewalPageContext.class);
    processSuccessfulRenewals.setAccessible(true);

    var loanId1 = randomUUID();
    var loanId2 = randomUUID();
    var loan1 = minimalLoanWithItem(loanId1, randomUUID());
    var loan2 = minimalLoanWithItem(loanId2, randomUUID());

    var loanRepository = mock(LoanRepository.class);
    var itemRepository = mock(ItemRepository.class);
    var overdueFinePolicyRepository = mock(OverdueFinePolicyRepository.class);

    when(loanRepository.updateLoan(loan1)).thenReturn(completedFuture(succeeded(loan1)));
    when(loanRepository.updateLoan(loan2)).thenReturn(completedFuture(succeeded(loan2)));
    // item.hasChanged() is false for a freshly built item, so updateItem is skipped

    var ctx1 = RenewalContext.create(loan1, new JsonObject(), "user-1");
    var ctx2 = RenewalContext.create(loan2, new JsonObject(), "user-1");

    var pageContext = new BulkRenewalPageContext(
      List.of(loan1, loan2), Map.of(), null,
      "user-1", List.of(ctx1, ctx2), Map.of());

    // Stub Clients: EventPublisher needs eventPublishingService to publish the due-date-changed event
    var clients = mock(Clients.class);
    var eventPublishingService = mock(EventPublishingService.class);
    when(clients.eventPublishingService()).thenReturn(eventPublishingService);
    when(eventPublishingService.publishEvent(any(), any(), any()))
      .thenReturn(CompletableFuture.completedFuture(null));

    // Act — bare Clients mock: downstream notice/fine services short-circuit or fire-and-forget
    var future =
      (CompletableFuture<Result<Void>>) processSuccessfulRenewals.invoke(
        runner,
        clients,
        loanRepository, itemRepository, overdueFinePolicyRepository,
        pageContext);
    var result = future.join();

    // Assert — pipeline succeeds and both loans were persisted
    assertTrue(result.succeeded());
    verify(loanRepository).updateLoan(loan1);
    verify(loanRepository).updateLoan(loan2);
  }

  private static Loan minimalLoanWithItem(UUID loanId, UUID itemId) {
    return new LoanBuilder()
      .withId(loanId)
      .withItemId(itemId)
      .asDomainObject()
      .withItem(Item.from(new io.vertx.core.json.JsonObject()
        .put("id", itemId.toString())
        .put("status", new io.vertx.core.json.JsonObject().put("name", "Checked out"))));
  }

  private static BulkRenewalJobGuard trackingGuard(CountDownLatch finished) {
    return new BulkRenewalJobGuard(finished::countDown);
  }

  private static final class RecordingOpenLoanPageRepository
    implements GetManyRecordsRepository<Loan> {

    private final List<MultipleRecords<Loan>> pages;
    private final List<Offset> offsets = new ArrayList<>();

    private RecordingOpenLoanPageRepository(MultipleRecords<Loan>... pages) {
      this.pages = List.of(pages);
    }

    @Override
    public CompletableFuture<Result<MultipleRecords<Loan>>> getMany(CqlQuery cqlQuery,
      PageLimit pageLimit, Offset offset) {

      offsets.add(offset);
      int pageIndex = offsets.size() - 1;

      if (pageIndex >= pages.size()) {
        return completedFuture(succeeded(MultipleRecords.empty()));
      }

      return completedFuture(succeeded(pages.get(pageIndex)));
    }

    private List<Offset> offsets() {
      return List.copyOf(offsets);
    }
  }

  private static final class RecordingBulkRenewOpenLoansService
    extends BulkRenewOpenLoansService {

    private final AtomicBoolean triggered = new AtomicBoolean(false);

    private RecordingBulkRenewOpenLoansService(BulkRenewalJobGuard guard) {
      super(guard, (jobId, ignored) ->
        CompletableFuture.completedFuture(Result.succeeded(null)));
    }

    @Override
    public boolean trigger(BulkRenewalWebContext detachedContext) {
      triggered.set(true);
      return true;
    }

    private boolean wasTriggered() {
      return triggered.get();
    }
  }

  private static MultipleRecords<Loan> page(Loan... loans) {
    return new MultipleRecords<>(List.of(loans), loans.length);
  }

  private static Loan[] loans(int count) {
    Loan[] loans = new Loan[count];

    for (int index = 0; index < count; index++) {
      loans[index] = loan(index + 1);
    }

    return loans;
  }

  private static Loan loan(int loanNumber) {
    return new LoanBuilder()
      .withId(UUID.fromString("00000000-0000-0000-0000-%012d".formatted(loanNumber)))
      .withUserId(randomUUID())
      .withItemId(randomUUID())
      .asDomainObject();
  }

  private static RenewalContext successfulRenewalContext(Loan loan, String userId) {
    return RenewalContext.create(loan, new JsonObject(), userId)
      .withRequestQueue(new RequestQueue(List.of()))
      .withTlrSettings(TlrSettingsConfiguration.defaultSettings());
  }

  private static final class TestAppender extends AbstractAppender {
    private final List<LogEvent> events = new ArrayList<>();

    private TestAppender() {
      super("BulkRenewOpenLoansServiceTestAppender", null,
        PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }

    private boolean hasMessageContaining(String fragment) {
      return events.stream()
        .map(event -> event.getMessage().getFormattedMessage())
        .anyMatch(message -> message.contains(fragment));
    }
  }

  private static final class TestFailure implements org.folio.circulation.support.HttpFailure {
    private final String message;

    private TestFailure(String message) {
      this.message = message;
    }

    @Override
    public void writeTo(io.vertx.core.http.HttpServerResponse response) {
      throw new UnsupportedOperationException(message);
    }
  }

  private static Loan loanWithItemAndUser(int loanNumber) {
    var loan = loan(loanNumber);
    var item = mock(Item.class);
    var user = mock(User.class);

    when(item.getItemId()).thenReturn("item-" + loanNumber);
    when(item.getInstanceId()).thenReturn("instance-" + loanNumber);
    when(user.getId()).thenReturn("user-" + loanNumber);

    return loan.withItem(item).withUser(user);
  }

  private static TlrSettingsConfiguration enabledTlrSettings() {
    return TlrSettingsConfiguration.from(new io.vertx.core.json.JsonObject()
      .put("titleLevelRequestsFeatureEnabled", true));
  }

  private static OverdueFinePolicy disallowRenewalWithReminderFeesPolicy() {
    return OverdueFinePolicy.from(new OverdueFinePolicyWithReminderFeesBuilder(
      randomUUID(), "Reminder policy")
      .withAllowRenewalOfItemsWithReminderFees(false)
      .create());
  }

  private static Response emptyResponse(String recordsPropertyName) {
    return new Response(200, new io.vertx.core.json.JsonObject()
      .put(recordsPropertyName, new io.vertx.core.json.JsonArray())
      .put("totalRecords", 0)
      .encode(), "application/json");
  }
}
