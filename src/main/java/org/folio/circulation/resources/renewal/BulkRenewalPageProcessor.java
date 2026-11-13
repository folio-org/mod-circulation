package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_INACTIVE;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class BulkRenewalPageProcessor {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final int DEFAULT_ID_CHUNK_SIZE = BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE;

  private final BulkRenewalCachedDependencies cachedDependencies;
  private final Function<Collection<String>, CompletableFuture<Result<MultipleRecords<Item>>>>
    itemFetcher;
  private final Function<Collection<String>, CompletableFuture<Result<Map<String, User>>>>
    userFetcher;
  private final BulkRenewalRequestQueueLookup requestQueueLookup;
  private final Function<Loan, CompletableFuture<Result<LoanPolicy>>>
    loanPolicyResolver;
  private final BiFunction<RenewalContext, CirculationErrorHandler,
    CompletableFuture<Result<RenewalContext>>> renewalCoordinator;
  private final OkapiPermissions okapiPermissions;
  private final JsonObject renewalRequest;
  private final EventPublisher eventPublisher;

  public BulkRenewalPageProcessor(BulkRenewalCachedDependencies cachedDependencies,
    Function<Collection<String>, CompletableFuture<Result<MultipleRecords<Item>>>> itemFetcher,
    Function<Collection<String>, CompletableFuture<Result<Map<String, User>>>> userFetcher,
    BulkRenewalRequestQueueLookup requestQueueLookup,
    Function<Loan, CompletableFuture<Result<LoanPolicy>>> loanPolicyResolver,
    BiFunction<RenewalContext, CirculationErrorHandler,
      CompletableFuture<Result<RenewalContext>>> renewalCoordinator,
    OkapiPermissions okapiPermissions,
    JsonObject renewalRequest,
    EventPublisher eventPublisher) {

    this.cachedDependencies = cachedDependencies;
    this.itemFetcher = itemFetcher;
    this.userFetcher = userFetcher;
    this.requestQueueLookup = requestQueueLookup;
    this.loanPolicyResolver = loanPolicyResolver;
    this.renewalCoordinator = renewalCoordinator;
    this.okapiPermissions = okapiPermissions == null ? OkapiPermissions.empty() : okapiPermissions;
    this.renewalRequest = renewalRequest == null ? new JsonObject() : renewalRequest.copy();
    this.eventPublisher = eventPublisher;
  }

  public CompletableFuture<Result<BulkRenewalPageContext>> processPage(
    MultipleRecords<Loan> records, String triggeringUserId, String jobId, int pageNumber) {

    final List<Loan> pageLoans = records == null
      ? List.of()
      : records.getRecords().stream()
        .filter(Objects::nonNull)
        .toList();

    if (pageLoans.isEmpty()) {
      return completedFuture(succeeded(new BulkRenewalPageContext(
        List.of(), Map.of(), null, triggeringUserId, jobId, pageNumber,
        List.of(), Map.of())));
    }

    return enrichPage(pageLoans)
      .thenCompose(result -> result.after(preparedPage ->
        filterEligibleLoans(preparedPage, triggeringUserId)
          .thenCompose(filterResult -> filterResult.after(filteredPage ->
            renewPage(filteredPage, triggeringUserId, jobId, pageNumber)))));
  }

  private CompletableFuture<Result<PreparedPage>> enrichPage(Collection<Loan> loans) {
    return cachedDependencies.getTlrSettings()
      .thenCompose(tlrSettingsResult -> cachedDependencies.getTimeZone()
        .thenCompose(timeZoneResult -> fetchItems(loans)
          .thenCompose(itemsResult -> fetchUsers(loans)
            .thenCompose(usersResult -> buildPreparedPage(loans, tlrSettingsResult,
              timeZoneResult, itemsResult, usersResult)))));
  }

  private CompletableFuture<Result<PreparedPage>> buildPreparedPage(Collection<Loan> loans,
    Result<TlrSettingsConfiguration> tlrSettingsResult, Result<ZoneId> timeZoneResult,
    Result<MultipleRecords<Item>> itemsResult, Result<Map<String, User>> usersResult) {

    return tlrSettingsResult.after(tlrSettings -> timeZoneResult.after(timeZone ->
      itemsResult.after(items -> usersResult.after(users -> {
        List<Loan> enrichedLoans = enrichLoans(loans, items, users);

        return requestQueueLookup.lookupByLoanId(enrichedLoans, tlrSettings)
          .thenApply(requestQueuesResult -> requestQueuesResult.map(requestQueues ->
            new PreparedPage(enrichedLoans, requestQueues, tlrSettings, timeZone)));
      }))));
  }

  private CompletableFuture<Result<FilteredPage>> filterEligibleLoans(
      PreparedPage preparedPage, String triggeringUserId) {

    List<Loan> loans = preparedPage.loans();
    Map<String, LoanPolicy> policiesByLoanId = new LinkedHashMap<>();

    // Resolve policies sequentially (failed lookups are skipped — the loan will be treated as
    // having no policy and silently excluded by the filter)
    CompletableFuture<Void> policyResolution = completedFuture(null);
    for (Loan loan : loans) {
      policyResolution = policyResolution.thenCompose(ignored ->
        loanPolicyResolver.apply(loan).thenAccept(result -> {
          if (result.succeeded()) {
            policiesByLoanId.put(loan.getId(), result.value());
          } else {
            log.debug("filterEligibleLoans:: loanId={} policy lookup failed, excluding from batch",
              loan.getId());
          }
        }));
    }

    return policyResolution.thenCompose(ignored -> {
      AutomaticRenewalEligibilityResult eligibility = BulkRenewalEligibilityFilter.filter(
        loans,
        policiesByLoanId,
        preparedPage.requestQueuesByLoanId(),
        preparedPage.timeZone());

      // Publish Circulation log entries for hold/recall exclusions
      List<CompletableFuture<Void>> logEvents = new ArrayList<>();
      for (Map.Entry<String, HttpFailure> entry : eligibility.holdRecallFailures().entrySet()) {
        String loanId = entry.getKey();
        String reason = entry.getValue().toString();
        loans.stream()
          .filter(l -> l.getId().equals(loanId))
          .findFirst()
          .ifPresent(loan -> logEvents.add(
            eventPublisher.publishAutomaticRenewalFailureLogEvent(loan, reason, triggeringUserId)
              .thenApply(r -> null)));
      }

      return CompletableFuture.allOf(logEvents.toArray(new CompletableFuture[0]))
        .thenApply(v -> succeeded(new FilteredPage(
          eligibility.eligible(),
          eligibility.holdRecallFailures(),
          preparedPage.requestQueuesByLoanId(),
          preparedPage.timeZone(),
          preparedPage.tlrSettings(),
          policiesByLoanId)));
    });
  }

  private CompletableFuture<Result<BulkRenewalPageContext>> renewPage(
    FilteredPage filteredPage, String triggeringUserId, String jobId, int pageNumber) {

    final List<RenewalContext> successfulRenewalContexts = new ArrayList<>();
    // Pre-populate with hold/recall failures recorded during eligibility filtering
    final Map<String, HttpFailure> failedRenewalsByLoanId =
      new LinkedHashMap<>(filteredPage.holdRecallFailures());

    CompletableFuture<Void> processing = completedFuture(null);

    for (Loan loan : filteredPage.loans()) {
      processing = processing.thenCompose(unused -> renewLoan(filteredPage, loan,
          triggeringUserId, jobId, pageNumber)
        .handle((result, error) -> {
          if (error != null) {
            failedRenewalsByLoanId.put(loan.getId(), failedDueToServerError(error).cause());
            return null;
          }

          if (result.succeeded()) {
            successfulRenewalContexts.add(result.value());
          } else {
            failedRenewalsByLoanId.put(loan.getId(), result.cause());
          }

          return null;
        }));
    }

    return processing.thenApply(unused -> succeeded(new BulkRenewalPageContext(
      filteredPage.loans(), filteredPage.requestQueuesByLoanId(), filteredPage.timeZone(),
      triggeringUserId, jobId, pageNumber, successfulRenewalContexts, failedRenewalsByLoanId)));
  }

  private CompletableFuture<Result<RenewalContext>> renewLoan(FilteredPage filteredPage,
    Loan loan, String triggeringUserId, String jobId, int pageNumber) {

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(okapiPermissions);
    RenewalContext renewalContext = createRenewalContext(filteredPage, loan,
      triggeringUserId, jobId, pageNumber);

    // Re-use the already-resolved policy from the eligibility filter phase to avoid
    // a second rules-engine call per loan.
    LoanPolicy resolvedPolicy = filteredPage.resolvedPolicies().get(loan.getId());

    return RenewalPreRenewalValidator.refuseWhenPatronIsInactive(succeeded(renewalContext),
        errorHandler, USER_IS_INACTIVE)
      .thenCompose(result -> renewAfterPreRenewalValidation(result, errorHandler, resolvedPolicy))
      .exceptionally(org.folio.circulation.support.results.CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<RenewalContext>> renewAfterPreRenewalValidation(
    Result<RenewalContext> result, CirculationErrorHandler errorHandler,
    LoanPolicy resolvedPolicy) {

    return result.after(renewalContext -> {
      CompletableFuture<Result<LoanPolicy>> policyFuture = resolvedPolicy != null
        ? completedFuture(succeeded(resolvedPolicy))
        : loanPolicyResolver.apply(renewalContext.getLoan());

      return policyFuture.thenCompose(policyResult -> policyResult.after(loanPolicy -> {
        RenewalContext contextWithPolicy = renewalContext.withLoan(
          renewalContext.getLoan().withLoanPolicy(loanPolicy));

        return renewalCoordinator.apply(contextWithPolicy, errorHandler)
          .thenApply(renewalResult -> finalizeRenewal(renewalResult, errorHandler));
      }));
    });
  }

  private RenewalContext createRenewalContext(FilteredPage filteredPage, Loan loan,
    String triggeringUserId, String jobId, int pageNumber) {

    return RenewalContext.create(loan.copy(), renewalRequest.copy(),
      triggeringUserId, performanceAnalysisId(jobId, pageNumber, loan), System.currentTimeMillis())
      .withRequestQueue(filteredPage.requestQueuesByLoanId()
        .getOrDefault(loan.getId(), new RequestQueue(List.of())))
      .withTimeZone(filteredPage.timeZone())
      .withTlrSettings(filteredPage.tlrSettings());
  }

  private Result<RenewalContext> finalizeRenewal(Result<RenewalContext> result,
    CirculationErrorHandler errorHandler) {

    return result.succeeded()
      ? errorHandler.failWithValidationErrors(result.value())
        .map(RenewalPostRenewalProcessor::unsetDueDateChangedByRecallIfNoOpenRecallsInQueue)
      : result;
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> fetchItems(Collection<Loan> loans) {
    final List<List<String>> itemIdPartitions = partitionsOf(loans.stream()
      .map(Loan::getItemId)
      .toList());

    if (itemIdPartitions.isEmpty()) {
      return completedFuture(succeeded(MultipleRecords.empty()));
    }

    CompletableFuture<Result<MultipleRecords<Item>>> fetchedItems = completedFuture(
      succeeded(MultipleRecords.empty()));

    for (List<String> partition : itemIdPartitions) {
      fetchedItems = fetchedItems.thenCompose(existing -> existing.combineAfter(
        ignored -> itemFetcher.apply(partition),
        MultipleRecords::combine));
    }

    return fetchedItems;
  }

  private CompletableFuture<Result<Map<String, User>>> fetchUsers(Collection<Loan> loans) {
    final List<List<String>> userIdPartitions = partitionsOf(loans.stream()
      .map(Loan::getUserId)
      .toList());

    if (userIdPartitions.isEmpty()) {
      return completedFuture(succeeded(Map.of()));
    }

    CompletableFuture<Result<Map<String, User>>> fetchedUsers = completedFuture(succeeded(Map.of()));

    for (List<String> partition : userIdPartitions) {
      fetchedUsers = fetchedUsers.thenCompose(existing -> existing.combineAfter(
        ignored -> userFetcher.apply(partition),
        BulkRenewalPageProcessor::mergeUsers));
    }

    return fetchedUsers;
  }

  private List<Loan> enrichLoans(Collection<Loan> loans, MultipleRecords<Item> items,
    Map<String, User> users) {

    final Map<String, Item> itemsById = items.getRecords().stream()
      .filter(Objects::nonNull)
      .filter(item -> item.getItemId() != null)
      .collect(toMap(Item::getItemId, Function.identity(), (first, second) -> first));

    return loans.stream()
      .map(loan -> loan
        .withItem(itemsById.getOrDefault(loan.getItemId(), loan.getItem()))
        .withUser(users.getOrDefault(loan.getUserId(), loan.getUser())))
      .toList();
  }

  private static Map<String, User> mergeUsers(Map<String, User> first,
    Map<String, User> second) {

    final Map<String, User> merged = new LinkedHashMap<>(first);
    merged.putAll(second);

    return merged;
  }

  private static List<List<String>> partitionsOf(Collection<String> ids) {
    return BulkRenewalChunkedFetchSupport.partition(ids, DEFAULT_ID_CHUNK_SIZE);
  }

  private static String performanceAnalysisId(String jobId, int pageNumber, Loan loan) {
    if (jobId == null) {
      return loan.getId();
    }

    return "%s:%s:%s".formatted(jobId, pageNumber, loan.getId());
  }

  private record PreparedPage(List<Loan> loans,
                              Map<String, RequestQueue> requestQueuesByLoanId,
                              TlrSettingsConfiguration tlrSettings,
                              ZoneId timeZone) {
  }

  private record FilteredPage(List<Loan> loans,
                              Map<String, HttpFailure> holdRecallFailures,
                              Map<String, RequestQueue> requestQueuesByLoanId,
                              ZoneId timeZone,
                              TlrSettingsConfiguration tlrSettings,
                              Map<String, LoanPolicy> resolvedPolicies) {
  }
}

