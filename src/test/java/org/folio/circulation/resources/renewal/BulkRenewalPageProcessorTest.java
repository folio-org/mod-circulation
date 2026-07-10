package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.policy.Period.days;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.NoFixedDueDateSchedules;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.validation.Validator;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.TlrSettingsConfigurationBuilder;
import api.support.builders.UserBuilder;
import io.vertx.core.json.JsonObject;

class BulkRenewalPageProcessorTest {

  @Test
  void shouldFetchTimeZoneAndTlrSettingsOnlyOnceAcrossPages() {
    AtomicInteger tlrLookups = new AtomicInteger();
    AtomicInteger timeZoneLookups = new AtomicInteger();
    TlrSettingsConfiguration tlrSettings = TlrSettingsConfiguration.from(
      new TlrSettingsConfigurationBuilder()
        .withTitleLevelRequestsFeatureEnabled(true)
        .create());
    ZoneId timeZone = ZoneId.of("America/Chicago");

    BulkRenewalCachedDependencies dependencies = new BulkRenewalCachedDependencies(
      () -> {
        tlrLookups.incrementAndGet();
        return completedFuture(succeeded(tlrSettings));
      },
      () -> {
        timeZoneLookups.incrementAndGet();
        return completedFuture(succeeded(timeZone));
      });

    assertSame(tlrSettings, dependencies.getTlrSettings().join().value());
    assertSame(timeZone, dependencies.getTimeZone().join().value());
    assertSame(tlrSettings, dependencies.getTlrSettings().join().value());
    assertSame(timeZone, dependencies.getTimeZone().join().value());

    assertEquals(1, tlrLookups.get());
    assertEquals(1, timeZoneLookups.get());
  }

  @Test
  void shouldReuseLoanPoliciesAcrossPagesWhenIdsAndAppliedRuleConditionsRepeat() {
    AtomicInteger loanPolicyLookups = new AtomicInteger();
    UUID policyId = UUID.randomUUID();
    AppliedRuleConditions firstPageConditions = new AppliedRuleConditions(true, false, true);
    AppliedRuleConditions secondPageConditions = new AppliedRuleConditions(true, false, true);
    LoanPolicy loanPolicy = loanPolicy(policyId, firstPageConditions);

    BulkRenewalCachedDependencies dependencies = new BulkRenewalCachedDependencies(
      () -> completedFuture(succeeded(TlrSettingsConfiguration.defaultSettings())),
      () -> completedFuture(succeeded(ZoneId.of("UTC"))));

    assertSame(loanPolicy, dependencies.getLoanPolicy(policyId.toString(),
      firstPageConditions, (ignoredId, ignoredConditions) -> {
        loanPolicyLookups.incrementAndGet();
        return completedFuture(succeeded(loanPolicy));
      }).join().value());

    assertSame(loanPolicy, dependencies.getLoanPolicy(policyId.toString(),
      secondPageConditions, (ignoredId, ignoredConditions) -> {
        loanPolicyLookups.incrementAndGet();
        return completedFuture(succeeded(loanPolicy));
      }).join().value());

    assertEquals(1, loanPolicyLookups.get());
  }

  @Test
  void shouldNotCollapseLoanPoliciesWhenAppliedRuleConditionsDiffer() {
    AtomicInteger loanPolicyLookups = new AtomicInteger();
    UUID policyId = UUID.randomUUID();
    AppliedRuleConditions firstConditions = new AppliedRuleConditions(true, false, false);
    AppliedRuleConditions secondConditions = new AppliedRuleConditions(false, true, false);
    LoanPolicy firstPolicy = loanPolicy(policyId, firstConditions);
    LoanPolicy secondPolicy = loanPolicy(policyId, secondConditions);

    BulkRenewalCachedDependencies dependencies = new BulkRenewalCachedDependencies(
      () -> completedFuture(succeeded(TlrSettingsConfiguration.defaultSettings())),
      () -> completedFuture(succeeded(ZoneId.of("UTC"))));

    assertSame(firstPolicy, dependencies.getLoanPolicy(policyId.toString(),
      firstConditions, (ignoredId, ignoredConditions) -> {
        loanPolicyLookups.incrementAndGet();
        return completedFuture(succeeded(firstPolicy));
      }).join().value());

    assertSame(secondPolicy, dependencies.getLoanPolicy(policyId.toString(),
      secondConditions, (ignoredId, ignoredConditions) -> {
        loanPolicyLookups.incrementAndGet();
        return completedFuture(succeeded(secondPolicy));
      }).join().value());

    assertEquals(2, loanPolicyLookups.get());
  }

  @Test
  void shouldReuseOverdueFinePoliciesAcrossPagesWhenIdsRepeat() {
    AtomicInteger overduePolicyLookups = new AtomicInteger();
    String policyId = UUID.randomUUID().toString();
    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.unknown(policyId);

    BulkRenewalCachedDependencies dependencies = new BulkRenewalCachedDependencies(
      () -> completedFuture(succeeded(TlrSettingsConfiguration.defaultSettings())),
      () -> completedFuture(succeeded(ZoneId.of("UTC"))));

    assertSame(overdueFinePolicy, dependencies.getOverdueFinePolicy(policyId, ignored -> {
      overduePolicyLookups.incrementAndGet();
      return completedFuture(succeeded(overdueFinePolicy));
    }).join().value());

    assertSame(overdueFinePolicy, dependencies.getOverdueFinePolicy(policyId, ignored -> {
      overduePolicyLookups.incrementAndGet();
      return completedFuture(succeeded(overdueFinePolicy));
    }).join().value());

    assertEquals(1, overduePolicyLookups.get());
  }

  @Test
  void shouldBuildRequestQueuesFromBatchedRequestLookupsInsteadOfCallingQueueRepositoryPerLoan() {
    AtomicInteger itemRequestLookups = new AtomicInteger();
    AtomicInteger instanceRequestLookups = new AtomicInteger();

    UUID instanceIdOne = UUID.randomUUID();
    UUID instanceIdTwo = UUID.randomUUID();
    UUID itemIdOne = UUID.randomUUID();
    UUID itemIdTwo = UUID.randomUUID();

    Loan firstLoan = loan(UUID.randomUUID(), itemIdOne, instanceIdOne);
    Loan secondLoan = loan(UUID.randomUUID(), itemIdTwo, instanceIdTwo);

    List<Request> openRequests = List.of(
      itemLevelRequest(UUID.randomUUID(), itemIdOne, instanceIdOne, 2),
      itemLevelRequest(UUID.randomUUID(), itemIdOne, instanceIdOne, 1),
      itemLevelRequest(UUID.randomUUID(), itemIdTwo, instanceIdTwo, 1));

    BulkRenewalRequestQueueLookup lookup = new BulkRenewalRequestQueueLookup(
      itemIds -> {
        itemRequestLookups.incrementAndGet();
        assertEquals(Set.of(itemIdOne.toString(), itemIdTwo.toString()), Set.copyOf(itemIds));
        return completedFuture(succeeded(openRequests));
      },
      instanceIds -> {
        instanceRequestLookups.incrementAndGet();
        return completedFuture(succeeded(List.of()));
      });

    Result<Map<String, RequestQueue>> result = lookup.lookupByLoanId(
      List.of(firstLoan, secondLoan), TlrSettingsConfiguration.defaultSettings()).join();

    assertTrue(result.succeeded());
    assertEquals(1, itemRequestLookups.get());
    assertEquals(0, instanceRequestLookups.get());
    assertEquals(List.of(1, 2), queuePositions(result.value().get(firstLoan.getId())));
    assertEquals(List.of(1), queuePositions(result.value().get(secondLoan.getId())));
  }

  @Test
  void shouldKeepAwaitingDeliveryRequestsInAssembledQueue() {
    UUID instanceId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    Loan loan = loan(UUID.randomUUID(), itemId, instanceId);

    List<Request> openRequests = List.of(
      itemLevelRequest(UUID.randomUUID(), itemId, instanceId, 1,
        RequestBuilder.OPEN_AWAITING_DELIVERY),
      itemLevelRequest(UUID.randomUUID(), itemId, instanceId, 2));

    BulkRenewalRequestQueueLookup lookup = new BulkRenewalRequestQueueLookup(
      itemIds -> completedFuture(succeeded(openRequests)),
      instanceIds -> completedFuture(succeeded(List.of())));

    Result<Map<String, RequestQueue>> result = lookup.lookupByLoanId(
      List.of(loan), TlrSettingsConfiguration.defaultSettings()).join();

    assertTrue(result.succeeded());
    assertEquals(List.of(
      RequestStatus.OPEN_AWAITING_DELIVERY,
      RequestStatus.OPEN_NOT_YET_FILLED), queueStatuses(result.value().get(loan.getId())));
  }

  @Test
  void shouldBuildTitleLevelQueuesFromBatchedInstanceRequestLookupsWhenTlrEnabled() {
    AtomicInteger itemRequestLookups = new AtomicInteger();
    AtomicInteger instanceRequestLookups = new AtomicInteger();

    UUID sharedInstanceId = UUID.randomUUID();
    UUID itemIdOne = UUID.randomUUID();
    UUID itemIdTwo = UUID.randomUUID();

    Loan firstLoan = loan(UUID.randomUUID(), itemIdOne, sharedInstanceId);
    Loan secondLoan = loan(UUID.randomUUID(), itemIdTwo, sharedInstanceId);

    List<Request> openRequests = List.of(
      titleLevelRequest(UUID.randomUUID(), sharedInstanceId, 1),
      itemLevelRequest(UUID.randomUUID(), itemIdOne, sharedInstanceId, 2),
      itemLevelRequest(UUID.randomUUID(), itemIdTwo, sharedInstanceId, 3));

    BulkRenewalRequestQueueLookup lookup = new BulkRenewalRequestQueueLookup(
      itemIds -> {
        itemRequestLookups.incrementAndGet();
        return completedFuture(succeeded(List.of()));
      },
      instanceIds -> {
        instanceRequestLookups.incrementAndGet();
        assertEquals(Set.of(sharedInstanceId.toString()), Set.copyOf(instanceIds));
        return completedFuture(succeeded(openRequests));
      });

    TlrSettingsConfiguration tlrSettings = TlrSettingsConfiguration.from(
      new TlrSettingsConfigurationBuilder()
        .withTitleLevelRequestsFeatureEnabled(true)
        .create());

    Result<Map<String, RequestQueue>> result = lookup.lookupByLoanId(
      List.of(firstLoan, secondLoan), tlrSettings).join();

    assertTrue(result.succeeded());
    assertEquals(0, itemRequestLookups.get());
    assertEquals(1, instanceRequestLookups.get());
    assertEquals(List.of(1, 2, 3), queuePositions(result.value().get(firstLoan.getId())));
    assertEquals(List.of(1, 2, 3), queuePositions(result.value().get(secondLoan.getId())));
  }

  @Test
  void shouldChunkOversizedItemRequestLookups() {
    int chunkSize = BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE;
    int loanCount = chunkSize * 2 + 5;
    List<Loan> loans = new ArrayList<>();
    Set<String> expectedItemIds = new HashSet<>();
    List<Set<String>> seenItemIdBatches = new ArrayList<>();
    AtomicInteger instanceRequestLookups = new AtomicInteger();

    for (int index = 0; index < loanCount; index++) {
      UUID itemId = UUID.randomUUID();
      UUID instanceId = UUID.randomUUID();
      loans.add(loan(UUID.randomUUID(), itemId, instanceId));
      expectedItemIds.add(itemId.toString());
    }

    BulkRenewalRequestQueueLookup lookup = new BulkRenewalRequestQueueLookup(
      itemIds -> {
        seenItemIdBatches.add(Set.copyOf(itemIds));
        return completedFuture(succeeded(List.of()));
      },
      instanceIds -> {
        instanceRequestLookups.incrementAndGet();
        return completedFuture(succeeded(List.of()));
      });

    Result<Map<String, RequestQueue>> result = lookup.lookupByLoanId(
      loans, TlrSettingsConfiguration.defaultSettings()).join();

    assertTrue(result.succeeded());
    assertEquals((loanCount + chunkSize - 1) / chunkSize, seenItemIdBatches.size());
    assertTrue(seenItemIdBatches.stream().allMatch(batch -> batch.size() <= chunkSize));
    assertEquals(expectedItemIds, seenItemIdBatches.stream()
      .flatMap(Set::stream)
      .collect(Collectors.toSet()));
    assertEquals(0, instanceRequestLookups.get());
  }

  @Test
  void shouldChunkOversizedInstanceRequestLookupsWhenTlrEnabled() {
    int chunkSize = BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE;
    int loanCount = chunkSize * 2 + 5;
    List<Loan> loans = new ArrayList<>();
    Set<String> expectedInstanceIds = new HashSet<>();
    List<Set<String>> seenInstanceIdBatches = new ArrayList<>();
    AtomicInteger itemRequestLookups = new AtomicInteger();

    for (int index = 0; index < loanCount; index++) {
      UUID itemId = UUID.randomUUID();
      UUID instanceId = UUID.randomUUID();
      loans.add(loan(UUID.randomUUID(), itemId, instanceId));
      expectedInstanceIds.add(instanceId.toString());
    }

    BulkRenewalRequestQueueLookup lookup = new BulkRenewalRequestQueueLookup(
      itemIds -> {
        itemRequestLookups.incrementAndGet();
        return completedFuture(succeeded(List.of()));
      },
      instanceIds -> {
        seenInstanceIdBatches.add(Set.copyOf(instanceIds));
        return completedFuture(succeeded(List.of()));
      });

    TlrSettingsConfiguration tlrSettings = TlrSettingsConfiguration.from(
      new TlrSettingsConfigurationBuilder()
        .withTitleLevelRequestsFeatureEnabled(true)
        .create());

    Result<Map<String, RequestQueue>> result = lookup.lookupByLoanId(loans, tlrSettings).join();

    assertTrue(result.succeeded());
    assertEquals((loanCount + chunkSize - 1) / chunkSize, seenInstanceIdBatches.size());
    assertTrue(seenInstanceIdBatches.stream().allMatch(batch -> batch.size() <= chunkSize));
    assertEquals(expectedInstanceIds, seenInstanceIdBatches.stream()
      .flatMap(Set::stream)
      .collect(Collectors.toSet()));
    assertEquals(0, itemRequestLookups.get());
  }

  @Test
  void shouldEnrichPageUsingCachedSettingsPoliciesAndBatchedReads() {
    AtomicInteger tlrLookups = new AtomicInteger();
    AtomicInteger timeZoneLookups = new AtomicInteger();
    AtomicInteger itemLookups = new AtomicInteger();
    AtomicInteger userLookups = new AtomicInteger();
    AtomicInteger itemRequestLookups = new AtomicInteger();
    AtomicInteger policyResolutionLookups = new AtomicInteger();
    AtomicInteger loanPolicyLookups = new AtomicInteger();

    UUID policyId = UUID.randomUUID();
    UUID instanceIdOne = UUID.randomUUID();
    UUID instanceIdTwo = UUID.randomUUID();
    UUID itemIdOne = UUID.randomUUID();
    UUID itemIdTwo = UUID.randomUUID();
    UUID userIdOne = UUID.randomUUID();
    UUID userIdTwo = UUID.randomUUID();
    AppliedRuleConditions conditions = new AppliedRuleConditions(true, false, true);
    LoanPolicy loanPolicy = loanPolicy(policyId, conditions);
    ZoneId timeZone = ZoneId.of("America/Chicago");

    Loan firstLoan = loan(UUID.randomUUID(), itemIdOne, instanceIdOne, userIdOne);
    Loan secondLoan = loan(UUID.randomUUID(), itemIdTwo, instanceIdTwo, userIdTwo);
    User firstUser = user(userIdOne);
    User secondUser = user(userIdTwo);

    BulkRenewalCachedDependencies dependencies = new BulkRenewalCachedDependencies(
      () -> {
        tlrLookups.incrementAndGet();
        return completedFuture(succeeded(TlrSettingsConfiguration.defaultSettings()));
      },
      () -> {
        timeZoneLookups.incrementAndGet();
        return completedFuture(succeeded(timeZone));
      });

    BulkRenewalRequestQueueLookup requestQueueLookup = new BulkRenewalRequestQueueLookup(
      itemIds -> {
        itemRequestLookups.incrementAndGet();
        assertEquals(Set.of(itemIdOne.toString(), itemIdTwo.toString()), Set.copyOf(itemIds));
        return completedFuture(succeeded(List.of()));
      },
      instanceIds -> completedFuture(succeeded(List.of())));

    BulkRenewalPageProcessor processor = pageProcessor(
      dependencies,
      itemIds -> {
        itemLookups.incrementAndGet();
        assertEquals(Set.of(itemIdOne.toString(), itemIdTwo.toString()), Set.copyOf(itemIds));
        return completedFuture(succeeded(new MultipleRecords<>(
          List.of(item(itemIdOne, instanceIdOne), item(itemIdTwo, instanceIdTwo)), 2)));
      },
      userIds -> {
        userLookups.incrementAndGet();
        assertEquals(Set.of(userIdOne.toString(), userIdTwo.toString()), Set.copyOf(userIds));
        return completedFuture(succeeded(Map.of(
          firstUser.getId(), firstUser,
          secondUser.getId(), secondUser)));
      },
      requestQueueLookup,
      loan -> {
        policyResolutionLookups.incrementAndGet();
        assertNotNull(loan.getItem());
        assertNotNull(loan.getUser());
        return dependencies.getLoanPolicy(policyId.toString(), conditions,
          (ignoredId, ignoredConditions) -> {
            loanPolicyLookups.incrementAndGet();
            return completedFuture(succeeded(loanPolicy));
          });
      },
      noOpRenewalCoordinator());

    Result<BulkRenewalPageContext> result = processor.processPage(
      page(firstLoan, secondLoan), "trigger-user", "job-1", 3).join();

    assertTrue(result.succeeded());
    assertEquals(1, tlrLookups.get());
    assertEquals(1, timeZoneLookups.get());
    assertEquals(1, itemLookups.get());
    assertEquals(1, userLookups.get());
    assertEquals(1, itemRequestLookups.get());
    assertEquals(2, policyResolutionLookups.get());
    assertEquals(1, loanPolicyLookups.get());
    assertEquals(2, result.value().successfulCount());
    assertEquals("trigger-user", result.value().triggeringUserId());
    assertEquals("job-1", result.value().jobId());
    assertEquals(3, result.value().pageNumber());
    assertEquals(timeZone, result.value().timeZone());
  }

  @Test
  void shouldSupplyFetchedItemBeforePolicyResolutionAndRenewalWhenLoanStartsWithoutItem() {
    UUID loanId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    AppliedRuleConditions conditions = new AppliedRuleConditions(false, false, false);
    AtomicInteger policyResolutionLookups = new AtomicInteger();
    AtomicInteger renewalCoordinatorCalls = new AtomicInteger();

    Loan loan = loanWithoutItem(loanId, itemId, userId);
    User user = user(userId);

    assertTrue(loan.getItem() == null);

    BulkRenewalPageProcessor processor = pageProcessor(
      defaultDependencies(),
      itemIds -> completedFuture(succeeded(new MultipleRecords<>(
        List.of(item(itemId, instanceId)), 1))),
      userIds -> completedFuture(succeeded(Map.of(user.getId(), user))),
      emptyRequestQueueLookup(),
      enrichedLoan -> {
        policyResolutionLookups.incrementAndGet();
        assertNotNull(enrichedLoan.getItem());
        assertEquals(itemId.toString(), enrichedLoan.getItem().getItemId());
        assertNotNull(enrichedLoan.getUser());

        return completedFuture(succeeded(renewableLoanPolicy(policyId, conditions)));
      },
      (context, errorHandler) -> {
        renewalCoordinatorCalls.incrementAndGet();
        assertNotNull(context.getLoan().getItem());
        assertEquals(itemId.toString(), context.getLoan().getItem().getItemId());
        return completedFuture(succeeded(context));
      });

    Result<BulkRenewalPageContext> result = processor.processPage(
      page(loan), "trigger-user", "job-enrich", 6).join();

    assertTrue(result.succeeded());
    assertEquals(1, policyResolutionLookups.get());
    assertEquals(1, renewalCoordinatorCalls.get());
    assertNotNull(result.value().successfulRenewalContexts().get(0).getLoan().getItem());
  }

  @Test
  void shouldContinueProcessingOtherLoansWhenOneLoanFailsValidation() {
    UUID blockedLoanId = UUID.randomUUID();
    UUID successfulLoanId = UUID.randomUUID();
    UUID blockedItemId = UUID.randomUUID();
    UUID successfulItemId = UUID.randomUUID();
    UUID blockedInstanceId = UUID.randomUUID();
    UUID successfulInstanceId = UUID.randomUUID();
    UUID blockedUserId = UUID.randomUUID();
    UUID successfulUserId = UUID.randomUUID();
    UUID blockedPolicyId = UUID.randomUUID();
    UUID successfulPolicyId = UUID.randomUUID();
    AppliedRuleConditions conditions = new AppliedRuleConditions(false, false, false);
    ZonedDateTime renewDate = ZonedDateTime.now();

    Loan blockedLoan = loan(blockedLoanId, blockedItemId, blockedInstanceId, blockedUserId)
      .changeDueDate(renewDate.plusDays(3));
    Loan successfulLoan = loan(successfulLoanId, successfulItemId, successfulInstanceId,
      successfulUserId).changeDueDate(renewDate.plusDays(5));

    LoanPolicy blockedPolicy = nonRenewableLoanPolicy(blockedPolicyId, conditions);
    LoanPolicy successfulPolicy = renewableLoanPolicy(successfulPolicyId, conditions);
    User blockedUser = user(blockedUserId);
    User successfulUser = user(successfulUserId);

    BulkRenewalPageProcessor processor = pageProcessor(
      defaultDependencies(),
      itemIds -> completedFuture(succeeded(new MultipleRecords<>(List.of(
        item(blockedItemId, blockedInstanceId), item(successfulItemId, successfulInstanceId)), 2))),
      userIds -> completedFuture(succeeded(Map.of(
        blockedUser.getId(), blockedUser,
        successfulUser.getId(), successfulUser))),
      emptyRequestQueueLookup(),
      loan -> completedFuture(succeeded(
        blockedLoan.getId().equals(loan.getId()) ? blockedPolicy : successfulPolicy)),
      regularRenewalCoordinator(renewDate));

    Result<BulkRenewalPageContext> result = processor.processPage(
      page(blockedLoan, successfulLoan), "trigger-user", "job-2", 4).join();

    assertTrue(result.succeeded());
    assertEquals(2, result.value().attemptedCount());
    assertEquals(1, result.value().successfulCount());
    assertEquals(1, result.value().failedCount());
    assertEquals(List.of(successfulLoanId.toString()), result.value().successfulLoans().stream()
      .map(Loan::getId)
      .toList());
    assertTrue(result.value().failedRenewalsByLoanId().get(blockedLoanId.toString())
      instanceof ValidationErrorFailure);
  }

  @Test
  void shouldPreserveSuccessfulLoanResultsAndFailureCountsInSamePage() {
    UUID blockedLoanId = UUID.randomUUID();
    UUID successfulLoanId = UUID.randomUUID();
    UUID blockedItemId = UUID.randomUUID();
    UUID successfulItemId = UUID.randomUUID();
    UUID blockedInstanceId = UUID.randomUUID();
    UUID successfulInstanceId = UUID.randomUUID();
    UUID blockedUserId = UUID.randomUUID();
    UUID successfulUserId = UUID.randomUUID();
    UUID blockedPolicyId = UUID.randomUUID();
    UUID successfulPolicyId = UUID.randomUUID();
    AppliedRuleConditions conditions = new AppliedRuleConditions(false, false, false);
    ZonedDateTime renewDate = ZonedDateTime.now();
    ZonedDateTime originalDueDate = renewDate.plusDays(7);

    Loan blockedLoan = loan(blockedLoanId, blockedItemId, blockedInstanceId, blockedUserId)
      .changeDueDate(originalDueDate);
    Loan successfulLoan = loan(successfulLoanId, successfulItemId, successfulInstanceId,
      successfulUserId).changeDueDate(originalDueDate);

    LoanPolicy blockedPolicy = nonRenewableLoanPolicy(blockedPolicyId, conditions);
    LoanPolicy successfulPolicy = renewableLoanPolicy(successfulPolicyId, conditions);
    User blockedUser = user(blockedUserId);
    User successfulUser = user(successfulUserId);

    BulkRenewalPageProcessor processor = pageProcessor(
      defaultDependencies(),
      itemIds -> completedFuture(succeeded(new MultipleRecords<>(List.of(
        item(blockedItemId, blockedInstanceId), item(successfulItemId, successfulInstanceId)), 2))),
      userIds -> completedFuture(succeeded(Map.of(
        blockedUser.getId(), blockedUser,
        successfulUser.getId(), successfulUser))),
      emptyRequestQueueLookup(),
      loan -> completedFuture(succeeded(
        blockedLoan.getId().equals(loan.getId()) ? blockedPolicy : successfulPolicy)),
      regularRenewalCoordinator(renewDate));

    Result<BulkRenewalPageContext> result = processor.processPage(
      page(blockedLoan, successfulLoan), "trigger-user", "job-3", 5).join();

    assertTrue(result.succeeded());
    assertEquals(1, result.value().successfulCount());
    assertEquals(1, result.value().failedCount());

    RenewalContext renewedContext = result.value().successfulRenewalContexts().get(0);

    assertEquals("trigger-user", renewedContext.getLoggedInUserId());
    assertTrue(renewedContext.getLoan().getDueDate().isAfter(originalDueDate));
    assertEquals(1, renewedContext.getLoan().getRenewalCount());
  }

  @Test
  void shouldUseTriggeringUserAsActingUserInRenewalContexts() {
    UUID loanId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    AppliedRuleConditions conditions = new AppliedRuleConditions(false, false, false);
    User user = user(userId);

    BulkRenewalPageProcessor processor = pageProcessor(
      defaultDependencies(),
      itemIds -> completedFuture(succeeded(new MultipleRecords<>(
        List.of(item(itemId, instanceId)), 1))),
      userIds -> completedFuture(succeeded(Map.of(user.getId(), user))),
      emptyRequestQueueLookup(),
      loan -> completedFuture(succeeded(renewableLoanPolicy(policyId, conditions))),
      noOpRenewalCoordinator());

    Result<BulkRenewalPageContext> result = processor.processPage(
      page(loan(loanId, itemId, instanceId, userId)), "triggering-user", "job-4", 1).join();

    assertTrue(result.succeeded());
    assertEquals("triggering-user", result.value().successfulRenewalContexts().get(0)
      .getLoggedInUserId());
  }

  @Test
  void shouldFailInactiveUsersWithoutCountingRenewalSuccessful() {
    UUID loanId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    AppliedRuleConditions conditions = new AppliedRuleConditions(false, false, false);
    AtomicInteger renewalCoordinatorCalls = new AtomicInteger();
    User inactiveUser = User.from(new UserBuilder()
      .withId(userId.toString())
      .inactive()
      .create());

    BulkRenewalPageProcessor processor = pageProcessor(
      defaultDependencies(),
      itemIds -> completedFuture(succeeded(new MultipleRecords<>(
        List.of(item(itemId, instanceId)), 1))),
      userIds -> completedFuture(succeeded(Map.of(inactiveUser.getId(), inactiveUser))),
      emptyRequestQueueLookup(),
      loan -> completedFuture(succeeded(renewableLoanPolicy(policyId, conditions))),
      (context, errorHandler) -> {
        renewalCoordinatorCalls.incrementAndGet();
        return completedFuture(succeeded(context));
      });

    Result<BulkRenewalPageContext> result = processor.processPage(
      page(loan(loanId, itemId, instanceId, userId)), "triggering-user", "job-5", 1).join();

    assertTrue(result.succeeded());
    assertEquals(0, result.value().successfulCount());
    assertEquals(1, result.value().failedCount());
    assertEquals(1, renewalCoordinatorCalls.get());
    ValidationErrorFailure failure = (ValidationErrorFailure) result.value()
      .failedRenewalsByLoanId().get(loanId.toString());
    assertEquals("Cannot renew loan when user is inactive or expired",
      failure.getErrors().stream().findFirst().orElseThrow().getMessage());
  }

  @Test
  void shouldUnsetDueDateChangedByRecallWhenNoOpenRecallRemains() {
    UUID loanId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    AppliedRuleConditions conditions = new AppliedRuleConditions(false, false, false);
    User user = user(userId);
    Loan recalledLoan = loan(loanId, itemId, instanceId, userId)
      .setDueDateChangedByRecall();

    BulkRenewalPageProcessor processor = pageProcessor(
      defaultDependencies(),
      itemIds -> completedFuture(succeeded(new MultipleRecords<>(
        List.of(item(itemId, instanceId)), 1))),
      userIds -> completedFuture(succeeded(Map.of(user.getId(), user))),
      emptyRequestQueueLookup(),
      loan -> completedFuture(succeeded(renewableLoanPolicy(policyId, conditions))),
      noOpRenewalCoordinator());

    Result<BulkRenewalPageContext> result = processor.processPage(
      page(recalledLoan), "triggering-user", "job-6", 1).join();

    assertTrue(result.succeeded());
    assertEquals(1, result.value().successfulCount());
    assertTrue(!result.value().successfulRenewalContexts().get(0).getLoan()
      .wasDueDateChangedByRecall());
  }

  @Test
  void shouldDeferPreparedPatronBlockValidationErrors() {
    UUID loanId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    User user = user(userId);
    RenewalContext context = RenewalContext.create(
        loan(loanId, itemId, instanceId, userId).withUser(user), new JsonObject(),
        "triggering-user")
      .withRequestQueue(new RequestQueue(List.of()));
    OverridingErrorHandler errorHandler = new OverridingErrorHandler(null);
    Validator<RenewalContext> validator = new Validator<>(renewalContext ->
      completedFuture(failed(singleValidationError(
        new ValidationError("Patron blocked from renewing")))));

    Result<RenewalContext> result = RenewalPreRenewalValidator
      .refuseWhenRenewalActionIsBlockedForPatron(validator, succeeded(context),
        errorHandler, USER_IS_BLOCKED_MANUALLY)
      .join();

    assertTrue(result.succeeded());

    Result<RenewalContext> finalized = errorHandler.failWithValidationErrors(result.value());

    assertTrue(finalized.failed());
    ValidationErrorFailure failure = (ValidationErrorFailure) finalized.cause();
    assertEquals("Patron blocked from renewing",
      failure.getErrors().stream().findFirst().orElseThrow().getMessage());
  }

  private Loan loan(UUID loanId, UUID itemId, UUID instanceId) {
    return loan(loanId, itemId, instanceId, null);
  }

  private Loan loan(UUID loanId, UUID itemId, UUID instanceId, UUID userId) {
    return new LoanBuilder()
      .withId(loanId)
      .withItemId(itemId)
      .withUserId(userId)
      .asDomainObject()
      .withItem(item(itemId, instanceId));
  }

  private Loan loanWithoutItem(UUID loanId, UUID itemId, UUID userId) {
    return new LoanBuilder()
      .withId(loanId)
      .withItemId(itemId)
      .withUserId(userId)
      .asDomainObject();
  }

  private Item item(UUID itemId, UUID instanceId) {
    Item item = mock(Item.class);

    when(item.isFound()).thenReturn(true);
    when(item.getItemId()).thenReturn(itemId.toString());
    when(item.getInstanceId()).thenReturn(instanceId.toString());
    when(item.getStatus()).thenReturn(ItemStatus.CHECKED_OUT);
    when(item.getStatusName()).thenReturn("Checked out");

    return item;
  }

  private User user(UUID userId) {
    return User.from(new UserBuilder().withId(userId.toString()).create());
  }

  private Request itemLevelRequest(UUID requestId, UUID itemId, UUID instanceId, int position) {
    return itemLevelRequest(requestId, itemId, instanceId, position,
      RequestBuilder.OPEN_NOT_YET_FILLED);
  }

  private Request itemLevelRequest(UUID requestId, UUID itemId, UUID instanceId, int position,
    String status) {

    return new RequestBuilder()
      .withId(requestId)
      .withItemId(itemId)
      .withInstanceId(instanceId)
      .withPosition(position)
      .withStatus(status)
      .itemRequestLevel()
      .hold()
      .asDomainObject();
  }

  private Request titleLevelRequest(UUID requestId, UUID instanceId, int position) {
    return new RequestBuilder()
      .withId(requestId)
      .withNoItemId()
      .withInstanceId(instanceId)
      .withPosition(position)
      .withStatus(RequestBuilder.OPEN_NOT_YET_FILLED)
      .titleRequestLevel()
      .hold()
      .asDomainObject();
  }

  private List<Integer> queuePositions(RequestQueue requestQueue) {
    return requestQueue.getRequests().stream()
      .map(Request::getPosition)
      .toList();
  }

  private List<RequestStatus> queueStatuses(RequestQueue requestQueue) {
    return requestQueue.getRequests().stream()
      .map(Request::getStatus)
      .toList();
  }

  private LoanPolicy loanPolicy(UUID policyId, AppliedRuleConditions ruleConditions) {
    return new LoanPolicy(new LoanPolicyBuilder().withId(policyId).create(),
      new NoFixedDueDateSchedules(), new NoFixedDueDateSchedules(), ruleConditions);
  }

  private LoanPolicy renewableLoanPolicy(UUID policyId, AppliedRuleConditions ruleConditions) {
    return new LoanPolicy(new LoanPolicyBuilder()
      .withId(policyId)
      .rolling(days(10))
      .renewFromCurrentDueDate()
      .create(), new NoFixedDueDateSchedules(), new NoFixedDueDateSchedules(),
      ruleConditions);
  }

  private LoanPolicy nonRenewableLoanPolicy(UUID policyId,
    AppliedRuleConditions ruleConditions) {

    return new LoanPolicy(new LoanPolicyBuilder()
      .withId(policyId)
      .rolling(days(10))
      .renewFromCurrentDueDate()
      .notRenewable()
      .create(), new NoFixedDueDateSchedules(), new NoFixedDueDateSchedules(),
      ruleConditions);
  }

  private MultipleRecords<Loan> page(Loan... loans) {
    return new MultipleRecords<>(List.of(loans), loans.length);
  }

  private BulkRenewalCachedDependencies defaultDependencies() {
    return new BulkRenewalCachedDependencies(
      () -> completedFuture(succeeded(TlrSettingsConfiguration.defaultSettings())),
      () -> completedFuture(succeeded(ZoneId.of("UTC"))));
  }

  private BulkRenewalRequestQueueLookup emptyRequestQueueLookup() {
    return new BulkRenewalRequestQueueLookup(
      itemIds -> completedFuture(succeeded(List.of())),
      instanceIds -> completedFuture(succeeded(List.of())));
  }

  private BulkRenewalPageProcessor pageProcessor(
    BulkRenewalCachedDependencies dependencies,
    java.util.function.Function<Collection<String>, CompletableFuture<Result<MultipleRecords<Item>>>>
      itemFetcher,
    java.util.function.Function<Collection<String>, CompletableFuture<Result<Map<String, User>>>>
      userFetcher,
    BulkRenewalRequestQueueLookup requestQueueLookup,
    java.util.function.Function<Loan, CompletableFuture<Result<LoanPolicy>>>
      loanPolicyResolver,
    BiFunction<RenewalContext, CirculationErrorHandler,
      CompletableFuture<Result<RenewalContext>>> renewalCoordinator) {

    return new BulkRenewalPageProcessor(dependencies, itemFetcher, userFetcher,
      requestQueueLookup, loanPolicyResolver, renewalCoordinator,
      OkapiPermissions.empty(), new JsonObject());
  }

  private BiFunction<RenewalContext, CirculationErrorHandler,
    CompletableFuture<Result<RenewalContext>>> noOpRenewalCoordinator() {

    return (context, errorHandler) -> completedFuture(succeeded(context));
  }

  private BiFunction<RenewalContext, CirculationErrorHandler,
    CompletableFuture<Result<RenewalContext>>> regularRenewalCoordinator(
      ZonedDateTime renewDate) {

    RenewByBarcodeResource resource = new RenewByBarcodeResource(null);

    return (context, errorHandler) -> completedFuture(
      resource.regularRenew(context, errorHandler, renewDate));
  }
}
