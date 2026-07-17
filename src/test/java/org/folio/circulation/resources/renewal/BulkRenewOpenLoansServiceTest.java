package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.Offset.zeroOffset;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.fetching.GetManyRecordsRepository;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import api.support.builders.LoanBuilder;
import api.support.builders.OverdueFinePolicyWithReminderFeesBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
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
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();
    CompletableFuture<Result<Void>> runningJob = new CompletableFuture<>();
    AtomicInteger invocations = new AtomicInteger();

    BulkRenewOpenLoansService service = new BulkRenewOpenLoansService(guard,
      (java.util.function.Function<String, CompletableFuture<Result<Void>>>) jobId -> {
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
  void shouldLogDetachedContextHeadersWithoutToken() {
    BulkRenewOpenLoansService service = new BulkRenewOpenLoansService(new BulkRenewalJobGuard(),
      (jobId, detachedContext) -> completedFuture(succeeded(null)));

    service.trigger(new BulkRenewalWebContext(Map.of(
      "x-okapi-url", "http://localhost:8082",
      "x-okapi-tenant", "diku",
      "x-okapi-token", "secret-token")));

    assertTrue(appender.hasMessageContaining("bulk renewal detached context headers="));
    assertTrue(appender.hasMessageContaining("x-okapi-url=http://localhost:8082"));
    assertFalse(appender.hasMessageContaining("secret-token"));
  }

  @Test
  void shouldAlwaysReleaseGuardWhenBackgroundJobFinishes() throws InterruptedException {
    TrackingBulkRenewalJobGuard guard = new TrackingBulkRenewalJobGuard();
    CompletableFuture<Result<Void>> runningJob = new CompletableFuture<>();

    BulkRenewOpenLoansService service = new BulkRenewOpenLoansService(guard,
      (java.util.function.Function<String, CompletableFuture<Result<Void>>>) jobId -> runningJob);

    assertTrue(service.trigger());
    assertTrue(guard.isRunning());

    runningJob.completeExceptionally(new RuntimeException("boom"));

    assertTrue(guard.awaitFinished());
    assertFalse(guard.isRunning());
  }

  @Test
  void shouldLogJobFinishedOnSuccessfulCompletion() throws InterruptedException {
    TrackingBulkRenewalJobGuard guard = new TrackingBulkRenewalJobGuard();

    BulkRenewOpenLoansService service = new BulkRenewOpenLoansService(guard,
      (java.util.function.Function<String, CompletableFuture<Result<Void>>>)
        jobId -> CompletableFuture.completedFuture(Result.succeeded(null)));

    assertTrue(service.trigger());
    assertTrue(guard.awaitFinished());
    assertTrue(appender.hasMessageContaining("bulk renewal job started"));
    assertTrue(appender.hasMessageContaining("bulk renewal job finished"));
  }

  @Test
  void shouldProcessOpenLoansPageByPageSequentially() throws InterruptedException {
    TrackingBulkRenewalJobGuard guard = new TrackingBulkRenewalJobGuard();
    RecordingOpenLoanPageRepository repository = new RecordingOpenLoanPageRepository(
      page(loans(500)),
      page(loan(3)));
    List<Integer> processedPages = new ArrayList<>();

    BulkRenewOpenLoansService service = new BulkRenewOpenLoansService(guard,
      new StubbedBackgroundJobRunner(
        repository,
        exactMatch("status.name", "Open"),
        (records, triggeringUserId, jobId, pageNumber) -> {
          assertEquals("trigger-user", triggeringUserId);
          processedPages.add(pageNumber);

          return completedFuture(succeeded(new BulkRenewalPageContext(
            List.copyOf(records.getRecords()),
            Map.of(),
            null,
            triggeringUserId,
            jobId,
            pageNumber,
            List.of(),
            Map.of())));
        },
        pageContext -> completedFuture(succeeded(null)),
        new BulkRenewalWebContext(Map.of("x-okapi-user-id", "trigger-user"))));

    assertTrue(service.trigger());
    assertTrue(guard.awaitFinished());

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
        "job-1",
        2,
        List.of(successfulRenewalContext(loan(1), "trigger-user", "job-1", 2)),
        Map.of("loan-1", new TestFailure("failed"))));

    assertTrue(releasedContext.loans().isEmpty());
    assertTrue(releasedContext.successfulRenewalContexts().isEmpty());
    assertTrue(releasedContext.failedRenewalsByLoanId().isEmpty());
    assertEquals("trigger-user", releasedContext.triggeringUserId());
    assertEquals("job-1", releasedContext.jobId());
    assertEquals(2, releasedContext.pageNumber());
  }

  @Test
  void shouldRejectTriggerWhenJobAlreadyRunningInSameInstance() throws InterruptedException {
    TrackingBulkRenewalJobGuard guard = new TrackingBulkRenewalJobGuard();
    CompletableFuture<Result<Void>> completion = new CompletableFuture<>();

    BulkRenewOpenLoansService service = new BulkRenewOpenLoansService(guard,
      new StubbedBackgroundJobRunner(
        new RecordingOpenLoanPageRepository(page(loan(1))),
        exactMatch("status.name", "Open"),
        (records, triggeringUserId, jobId, pageNumber) -> completedFuture(succeeded(
          new BulkRenewalPageContext(List.copyOf(records.getRecords()), Map.of(), null,
            triggeringUserId,
            jobId, pageNumber, List.of(), Map.of()))),
        pageContext -> completion,
        new BulkRenewalWebContext(Map.of("x-okapi-user-id", "trigger-user"))));

    assertTrue(service.trigger());
    assertFalse(service.trigger());

    completion.complete(succeeded(null));

    assertTrue(guard.awaitFinished());
  }

  @Test
  void shouldUseChunkAwareRealRunnerFetchSeams() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    UserRepository userRepository = Mockito.mock(UserRepository.class);
    RequestRepository requestRepository = Mockito.mock(RequestRepository.class);
    int requestCqlChunkSize = 50;
    List<String> ids = List.of("a", "b");
    Loan loan = loanWithItemAndUser(1);
    String itemId = loan.getItemId();

    when(itemRepository.fetchFor(ids, BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE))
      .thenReturn(completedFuture(succeeded(MultipleRecords.empty())));
    when(userRepository.getUsersForUserIds(ids, BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE))
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

    verify(itemRepository).fetchFor(ids, BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE);
    verify(itemRepository, never()).fetchFor(ids);
    verify(userRepository).getUsersForUserIds(ids, BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE);
    verify(userRepository, never()).getUsersForUserIds(ids);
    verify(requestRepository).findOpenRequestsByItemIds(List.of(itemId), requestCqlChunkSize);
    verify(requestRepository, never()).findOpenRequestsByItemIds(List.of(itemId));
    verify(requestRepository).findOpenRequestsByInstanceIds(List.of("instance-1"),
      requestCqlChunkSize);
    verify(requestRepository, never()).findOpenRequestsByInstanceIds(List.of("instance-1"));
  }

  @Test
  void shouldUseFullLoanCompatiblePolicyResolver() {
    LoanPolicyRepository loanPolicyRepository = Mockito.mock(LoanPolicyRepository.class);
    RequestRepository requestRepository = Mockito.mock(RequestRepository.class);
    BulkRenewalCachedDependencies cachedDependencies = new BulkRenewalCachedDependencies(
      () -> completedFuture(succeeded(TlrSettingsConfiguration.defaultSettings())),
      () -> completedFuture(succeeded(java.time.ZoneId.of("UTC"))));
    Loan loan = loanWithItemAndUser(1);
    LoanPolicy loanPolicy = Mockito.mock(LoanPolicy.class);
    CirculationRuleMatch match = new CirculationRuleMatch("policy-1",
      new AppliedRuleConditions(true, true, true));

    when(loanPolicyRepository.lookupPolicyForBulkRenewal(loan, requestRepository))
      .thenReturn(completedFuture(succeeded(match)));
    when(loanPolicyRepository.lookupPolicy("policy-1", match.getAppliedRuleConditions()))
      .thenReturn(completedFuture(succeeded(loanPolicy)));

    Result<LoanPolicy> result = BulkRenewOpenLoansService.RealRunnerFactories.loanPolicyResolver(
      cachedDependencies, loanPolicyRepository, requestRepository).apply(loan).join();

    assertTrue(result.succeeded());
    assertEquals(loanPolicy, result.value());
    verify(loanPolicyRepository).lookupPolicyForBulkRenewal(loan, requestRepository);
    verify(loanPolicyRepository, never()).lookupPolicyId(loan);
  }

  @Test
  void shouldBlockPreparedRenewalWhenReminderFeesDisallowRenewal() {
    ZonedDateTime now = ZonedDateTime.now();
    Loan loan = loanWithItemAndUser(1)
      .withOverdueFinePolicy(disallowRenewalWithReminderFeesPolicy())
      .withRemindersLastFeeBilled(1, now);
    RenewalContext context = successfulRenewalContext(loan, "trigger-user", "job-reminder", 1);
    OverridingErrorHandler errorHandler = new OverridingErrorHandler(null);
    AtomicInteger renewalLogicCalls = new AtomicInteger();

    Result<RenewalContext> result = BulkRenewOpenLoansService.renewPreparedLoan(
      context,
      errorHandler,
      overdueFineContext -> completedFuture(succeeded(overdueFineContext)),
      (renewalContext, handler) -> {
        renewalLogicCalls.incrementAndGet();
        return completedFuture(succeeded(renewalContext));
      }).join();

    assertTrue(result.failed());
    assertEquals(0, renewalLogicCalls.get());

    ValidationErrorFailure failure = (ValidationErrorFailure) result.cause();
    assertEquals("Renewals not allowed for loans with reminders.",
      failure.getErrors().stream().findFirst().orElseThrow().getMessage());
  }

  @Test
  void shouldTriggerServiceFromResourceAndReturnNoContentImmediately(Vertx vertx,
    VertxTestContext testContext) {

    Router router = Router.router(vertx);
    HttpClient client = vertx.createHttpClient();
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();
    RecordingBulkRenewOpenLoansService service = new RecordingBulkRenewOpenLoansService(guard);

    new BulkRenewOpenLoansResource(client, guard, service).register(router);

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

  private static final class TrackingBulkRenewalJobGuard extends BulkRenewalJobGuard {
    private final CountDownLatch finished = new CountDownLatch(1);

    @Override
    public void finish() {
      super.finish();
      finished.countDown();
    }

    private boolean awaitFinished() throws InterruptedException {
      return finished.await(5, SECONDS);
    }
  }

  private static final class StubbedBackgroundJobRunner
    implements BulkRenewOpenLoansService.BackgroundJobRunner {

    private final GetManyRecordsRepository<Loan> repository;
    private final Result<CqlQuery> query;
    private final BulkRenewOpenLoansService.PageProcessor pageProcessor;
    private final BulkRenewOpenLoansService.PageCompletionHandler pageCompletionHandler;
    private final BulkRenewalWebContext detachedContext;

    private StubbedBackgroundJobRunner(GetManyRecordsRepository<Loan> repository,
      Result<CqlQuery> query,
      BulkRenewOpenLoansService.PageProcessor pageProcessor,
      BulkRenewOpenLoansService.PageCompletionHandler pageCompletionHandler,
      BulkRenewalWebContext detachedContext) {

      this.repository = repository;
      this.query = query;
      this.pageProcessor = pageProcessor;
      this.pageCompletionHandler = pageCompletionHandler;
      this.detachedContext = detachedContext;
    }

    @Override
    public BulkRenewOpenLoansService.RealJobComponents create(String jobId) {
      return new BulkRenewOpenLoansService.RealJobComponents(repository, query, pageProcessor,
        pageCompletionHandler, detachedContext);
    }
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
      super(guard, (java.util.function.Function<String, CompletableFuture<Result<Void>>>)
        jobId -> CompletableFuture.completedFuture(Result.succeeded(null)));
    }

    @Override
    public boolean trigger() {
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
      .withUserId(UUID.randomUUID())
      .withItemId(UUID.randomUUID())
      .asDomainObject();
  }

  private static RenewalContext successfulRenewalContext(Loan loan, String userId, String jobId,
    int pageNumber) {

    return RenewalContext.create(loan, new io.vertx.core.json.JsonObject(), userId,
      "%s:%s".formatted(jobId, pageNumber), System.currentTimeMillis())
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
    Loan loan = loan(loanNumber);
    Item item = Mockito.mock(Item.class);
    User user = Mockito.mock(User.class);

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
      UUID.randomUUID(), "Reminder policy")
      .withAllowRenewalOfItemsWithReminderFees(false)
      .create());
  }
}
