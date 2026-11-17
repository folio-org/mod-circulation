package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.headersAsString;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.OverdueFineService;
import org.folio.circulation.domain.OverduePeriodCalculatorService;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.ReminderFeeScheduledNoticeService;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.domain.validation.Validator;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.LoanNoticeSender;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.services.CirculationSettingsService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.FeeFineFacade;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.fetching.GetManyRecordsRepository;
import org.folio.circulation.support.fetching.PageableFetcher;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class BulkRenewOpenLoansService {
  private static final Logger log = LogManager.getLogger(BulkRenewOpenLoansService.class);
  private static final int BULK_FETCH_CHUNK_SIZE = BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE;
  private static final int REQUEST_CQL_FETCH_CHUNK_SIZE = 50;

  private final BulkRenewalJobGuard guard;
  private final DetachedContextJobRunner jobRunner;

  public BulkRenewOpenLoansService(BulkRenewalJobGuard guard,
    DetachedContextJobRunner jobRunner) {

    this.guard = guard;
    this.jobRunner = jobRunner;
  }

  /**
   * Production factory: wraps the real {@link RealBackgroundJobRunner} so that callers
   * do not need to know about it.
   */
  public static BulkRenewOpenLoansService forHttpClient(BulkRenewalJobGuard guard,
    HttpClient httpClient) {

    return new BulkRenewOpenLoansService(guard,
      (jobId, detachedContext) -> new RealBackgroundJobRunner(httpClient,
        detachedContext != null ? detachedContext : new BulkRenewalWebContext(Map.of()))
        .create(jobId)
        .run(jobId));
  }

  /**
   * Test convenience: adapts a plain {@code jobId -> future} function so that tests which
   * do not need a {@link BulkRenewalWebContext} do not have to handle it.
   */
  static BulkRenewOpenLoansService forJobRunner(BulkRenewalJobGuard guard,
    Function<String, CompletableFuture<Result<Void>>> runner) {

    return new BulkRenewOpenLoansService(guard, (jobId, ignored) -> runner.apply(jobId));
  }

  public boolean trigger() {
    return trigger(null);
  }

  public boolean trigger(BulkRenewalWebContext detachedContext) {
    log.debug("bulk renewal detached context headers={}",
      detachedContext == null ? "" : headersAsString(detachedContext.getHeaders()));
    return triggerInternal(jobId -> jobRunner.run(jobId, detachedContext));
  }

  public static CompletableFuture<Result<Void>> noOpRunner(String ignored,
    BulkRenewalWebContext ignoredContext) {

    return completedFuture(Result.succeeded(null));
  }

  static CompletableFuture<Result<RenewalContext>> renewPreparedLoan(
    RenewalContext renewalContext,
    CirculationErrorHandler errorHandler,
    Function<RenewalContext, CompletableFuture<Result<RenewalContext>>> overdueFinePolicyLookup,
    BiFunction<RenewalContext, CirculationErrorHandler,
      CompletableFuture<Result<RenewalContext>>> renewalLogic) {

    return completedFuture(succeeded(renewalContext))
      .thenCompose(result -> result.after(overdueFinePolicyLookup))
      .thenCompose(result -> result.after(context ->
        RenewalPreRenewalValidator.blockRenewalOfItemsWithReminderFees(context, errorHandler)))
      .thenCompose(result -> result.after(context -> renewalLogic.apply(context, errorHandler)));
  }

  static BulkRenewalPageContext releasePageReferences(BulkRenewalPageContext pageContext) {
    return new BulkRenewalPageContext(List.of(), Map.of(), pageContext.timeZone(),
      pageContext.triggeringUserId(), List.of(), Map.of());
  }

  private boolean triggerInternal(Function<String, CompletableFuture<Result<Void>>> runner) {
    if (!guard.tryStart()) {
      log.info("bulk renewal job already running");
      return false;
    }

    final String jobId = UUID.randomUUID().toString();
    log.info("bulk renewal job started jobId={}", jobId);

    runJob(jobId, runner)
      .whenComplete((result, error) -> {
        if (error != null) {
          log.error("bulk renewal job failed jobId={}", jobId, error);
        }
        else if (result != null && result.failed()) {
          log.error("bulk renewal job failed jobId={} cause={}", jobId,
            result.cause());
        }

        log.info("bulk renewal job finished jobId={}", jobId);
        guard.finish();
      });

    return true;
  }

  private CompletableFuture<Result<Void>> runJob(String jobId,
    Function<String, CompletableFuture<Result<Void>>> runner) {

    try {
      return runner.apply(jobId)
        .exceptionally(org.folio.circulation.support.results.CommonFailures::failedDueToServerError);
    }
    catch (Exception e) {
      return completedFuture(failedDueToServerError(e));
    }
  }

  @FunctionalInterface
  public interface DetachedContextJobRunner {
    CompletableFuture<Result<Void>> run(String jobId, BulkRenewalWebContext detachedContext);
  }

  @FunctionalInterface
  interface PageProcessor {
    CompletableFuture<Result<BulkRenewalPageContext>> process(MultipleRecords<Loan> records,
      String triggeringUserId, String jobId, int pageNumber);
  }

  @FunctionalInterface
  interface PageCompletionHandler {
    CompletableFuture<Result<Void>> complete(BulkRenewalPageContext pageContext);
  }

  static final class RealRunnerFactories {
    private RealRunnerFactories() {
    }

    static Function<java.util.Collection<String>, CompletableFuture<Result<MultipleRecords<org.folio.circulation.domain.Item>>>>
      itemFetcher(ItemRepository itemRepository) {

      return itemIds -> itemRepository.fetchFor(itemIds, BULK_FETCH_CHUNK_SIZE);
    }

    static Function<java.util.Collection<String>, CompletableFuture<Result<Map<String, org.folio.circulation.domain.User>>>>
      userFetcher(UserRepository userRepository) {

      return userIds -> userRepository.getUsersForUserIds(userIds, BULK_FETCH_CHUNK_SIZE);
    }

    static BulkRenewalRequestQueueLookup requestQueueLookup(RequestRepository requestRepository) {
      return new BulkRenewalRequestQueueLookup(
        itemIds -> requestRepository.findOpenRequestsByItemIds(itemIds, REQUEST_CQL_FETCH_CHUNK_SIZE)
          .thenApply(result -> result.map(MultipleRecords::getRecords)),
        instanceIds -> requestRepository.findOpenRequestsByInstanceIds(instanceIds,
            REQUEST_CQL_FETCH_CHUNK_SIZE)
          .thenApply(result -> result.map(MultipleRecords::getRecords)));
    }

    static Function<Loan, CompletableFuture<Result<LoanPolicy>>> loanPolicyResolver(
      BulkRenewalCachedDependencies cachedDependencies,
      LoanPolicyRepository loanPolicyRepository) {

      return loan -> loanPolicyRepository.lookupPolicyForBulkRenewal(loan, cachedDependencies)
        .thenCompose(matchResult -> matchResult.after(match -> cachedDependencies.getLoanPolicy(
          match.getPolicyId(),
          match.getAppliedRuleConditions(),
          loanPolicyRepository::lookupPolicy)));
    }
  }

  static final class RealJobComponents {
    private final GetManyRecordsRepository<Loan> repository;
    private final Result<CqlQuery> query;
    private final PageProcessor pageProcessor;
    private final PageCompletionHandler pageCompletionHandler;
    private final BulkRenewalWebContext detachedContext;

    RealJobComponents(GetManyRecordsRepository<Loan> repository, Result<CqlQuery> query,
      PageProcessor pageProcessor, PageCompletionHandler pageCompletionHandler,
      BulkRenewalWebContext detachedContext) {

      this.repository = repository;
      this.query = query;
      this.pageProcessor = pageProcessor;
      this.pageCompletionHandler = pageCompletionHandler;
      this.detachedContext = detachedContext;
    }

    CompletableFuture<Result<Void>> run(String jobId) {
      AtomicInteger pageCounter = new AtomicInteger();
      PageableFetcher<Loan> fetcher = new PageableFetcher<>(repository);
      String triggeringUserId = detachedContext.getUserId();

      return query.after(cqlQuery -> fetcher.processPages(cqlQuery, records -> {
        int pageNumber = pageCounter.incrementAndGet();

        log.info("bulk renewal page start jobId={} page={} records={}", jobId, pageNumber,
          records.size());

        return pageProcessor.process(records, triggeringUserId, jobId, pageNumber)
          .thenCompose(result -> result.after(pageContext -> pageCompletionHandler.complete(pageContext)
            .thenApply(completion -> completion.map(ignored -> releasePageReferences(pageContext)))
            .thenApply(Result::mapEmpty)))
          .thenApply(result -> {
            log.info("bulk renewal page finish jobId={} page={} records={}", jobId, pageNumber,
              records.size());
            return result;
          });
      }));
    }
  }

  private static final class RealBackgroundJobRunner {
    private final HttpClient httpClient;
    private final BulkRenewalWebContext detachedContext;

    private RealBackgroundJobRunner(HttpClient httpClient,
      BulkRenewalWebContext detachedContext) {

      this.httpClient = httpClient;
      this.detachedContext = detachedContext;
    }

    RealJobComponents create(String jobId) {
      WebContext context = new WebContext(
        detachedContext.getHeaders(), detachedContext.getVertxContext());
      Clients clients = Clients.create(context, httpClient);
      ItemRepository itemRepository = new ItemRepository(clients);
      UserRepository userRepository = new UserRepository(clients);
      LoanRepository loanRepository = new LoanRepository(clients, itemRepository, userRepository);
      RequestRepository requestRepository = new RequestRepository(clients);
      LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
      OverdueFinePolicyRepository overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
      CirculationSettingsService circulationSettingsService = new CirculationSettingsService(clients);
      SettingsRepository settingsRepository = new SettingsRepository(clients);
      AutomatedPatronBlocksRepository automatedPatronBlocksRepository =
        new AutomatedPatronBlocksRepository(clients);
      BulkRenewalCachedDependencies cachedDependencies = new BulkRenewalCachedDependencies(
        circulationSettingsService, settingsRepository);
      BulkRenewalRequestQueueLookup requestQueueLookup = RealRunnerFactories.requestQueueLookup(
        requestRepository);
      OkapiPermissions okapiPermissions = OkapiPermissions.from(detachedContext.getHeaders());
      JsonObject renewalRequest = new JsonObject();
      RenewByBarcodeResource renewResource = new RenewByBarcodeResource(httpClient);

      BulkRenewalPageProcessor pageProcessor = new BulkRenewalPageProcessor(
        cachedDependencies,
        RealRunnerFactories.itemFetcher(itemRepository),
        RealRunnerFactories.userFetcher(userRepository),
        requestQueueLookup,
        RealRunnerFactories.loanPolicyResolver(cachedDependencies, loanPolicyRepository),
        (renewalContext, errorHandler) -> renewLoan(clients, renewResource, renewalContext,
          errorHandler, okapiPermissions, automatedPatronBlocksRepository),
        okapiPermissions,
        renewalRequest,
        new EventPublisher(clients));

      return new RealJobComponents(loanRepository, loanRepository.createOpenLoanPageQuery(),
        pageProcessor::processPage,
        pageContext -> processSuccessfulRenewals(clients, loanRepository, itemRepository,
          overdueFinePolicyRepository, pageContext),
        detachedContext);
    }

    private CompletableFuture<Result<RenewalContext>> renewLoan(Clients clients,
      RenewByBarcodeResource renewResource, RenewalContext renewalContext,
      CirculationErrorHandler errorHandler, OkapiPermissions okapiPermissions,
      AutomatedPatronBlocksRepository automatedPatronBlocksRepository) {

      return applyPatronBlockValidations(clients, okapiPermissions,
          automatedPatronBlocksRepository, renewalContext, errorHandler)
        .thenCompose(result -> result.after(context -> renewPreparedLoan(context, errorHandler,
          overdueFineContext -> new OverdueFinePolicyRepository(clients)
            .findOverdueFinePolicyForLoan(succeeded(overdueFineContext.getLoan()))
            .thenApply(overdueFinePolicyResult ->
              overdueFinePolicyResult.map(overdueFineContext::withLoan)),
          (preparedContext, handler) -> applyRenewalLogic(clients, renewResource,
            preparedContext, handler))));
    }

    private CompletableFuture<Result<RenewalContext>> applyPatronBlockValidations(
      Clients clients, OkapiPermissions okapiPermissions,
      AutomatedPatronBlocksRepository automatedPatronBlocksRepository,
      RenewalContext renewalContext, CirculationErrorHandler errorHandler) {

      JsonObject renewalRequest = renewalContext.getRenewalRequest();
      Validator<RenewalContext> manualPatronBlocksValidator =
        RenewalPreRenewalValidator.createManualPatronBlocksValidator(renewalRequest,
          okapiPermissions, clients);
      Validator<RenewalContext> automatedPatronBlocksValidator =
        RenewalPreRenewalValidator.createAutomatedPatronBlocksValidator(renewalRequest,
          okapiPermissions, automatedPatronBlocksRepository);

      return RenewalPreRenewalValidator.refuseWhenRenewalActionIsBlockedForPatron(
        manualPatronBlocksValidator, succeeded(renewalContext), errorHandler,
        USER_IS_BLOCKED_MANUALLY)
        .thenCompose(result -> RenewalPreRenewalValidator.refuseWhenRenewalActionIsBlockedForPatron(
          automatedPatronBlocksValidator, result, errorHandler,
          USER_IS_BLOCKED_AUTOMATICALLY));
    }

    private CompletableFuture<Result<RenewalContext>> applyRenewalLogic(Clients clients,
      RenewByBarcodeResource renewResource, RenewalContext context,
      CirculationErrorHandler errorHandler) {

      ZonedDateTime systemTime = org.folio.circulation.support.utils.ClockUtil.getZonedDateTime();
      ClosedLibraryStrategyService strategyService = ClosedLibraryStrategyService.using(clients,
        systemTime, true);

      Result<RenewalContext> renewResult = renewResource.regularRenew(context, errorHandler,
        systemTime);

      return renewResult.after(strategyService::applyClosedLibraryDueDateManagement);
    }

    private CompletableFuture<Result<Void>> processSuccessfulRenewals(Clients clients,
      LoanRepository loanRepository, ItemRepository itemRepository,
      OverdueFinePolicyRepository overdueFinePolicyRepository,
      BulkRenewalPageContext pageContext) {

      CompletableFuture<Result<Void>> processing = completedFuture(succeeded(null));

      StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);
      FeeFineScheduledNoticeService feeFineNoticeService = FeeFineScheduledNoticeService.using(clients);
      LoanScheduledNoticeService loanScheduledNoticeService = LoanScheduledNoticeService.using(clients);
      ReminderFeeScheduledNoticeService reminderFeeScheduledNoticeService =
        new ReminderFeeScheduledNoticeService(clients);
      LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients, loanRepository);
      EventPublisher eventPublisher = new EventPublisher(clients);
      OverdueFineService overdueFineService = new OverdueFineService(
        overdueFinePolicyRepository,
        itemRepository,
        new FeeFineOwnerRepository(clients),
        new FeeFineRepository(clients),
        ScheduledNoticesRepository.using(clients),
        new OverduePeriodCalculatorService(new CalendarRepository(clients),
          new LoanPolicyRepository(clients)),
        new FeeFineFacade(clients));

      for (RenewalContext context : pageContext.successfulRenewalContexts()) {
        processing = processing.thenCompose(result -> result.after(ignored ->
          storeLoanAndItem.updateLoanAndItemInStorage(context)
            .thenCompose(stored -> stored.after(overdueFineService::createOverdueFineIfNecessary))
            .thenApply(updated -> updated.next(feeFineNoticeService::scheduleOverdueFineNotices))
            .thenCompose(updated -> updated.after(eventPublisher::publishDueDateChangedEvent))
            .thenApply(updated -> updated.next(loanScheduledNoticeService::rescheduleDueDateNotices))
            .thenApply(updated -> updated.next(reminderFeeScheduledNoticeService::rescheduleFirstReminder))
            .thenApply(updated -> updated.next(loanNoticeSender::sendRenewalPatronNotice))
            .thenApply(updated -> updated.mapEmpty())));
      }

      return processing.thenApply(result -> result.map(ignored -> null));
    }
  }
}
