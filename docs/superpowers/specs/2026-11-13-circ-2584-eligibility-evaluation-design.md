# Design: CIRC-2584 — Eligibility Evaluation for Bulk Auto-Renewal

**Ticket:** CIRC-2584 — Eligibility Evaluation Respects Policy & Rules  
**Base branch:** CIRC-2623 (bulk renewal POC)  
**Status:** Approved for implementation

---

## Purpose

The CIRC-2623 POC attempts to renew every open loan without checking whether the
governing Loan Policy actually enables automatic renewals or whether the loan is within
the configured eligibility window. This design updates the bulk renewal flow to filter
loans to only those eligible for automatic renewal before passing them to the renewal
engine, and to emit Circulation log entries for loans blocked by an active hold or
recall.

---

## Architecture Overview

A new **eligibility filter phase** is inserted between `enrichPage()` and `renewPage()`
inside `BulkRenewalPageProcessor`. The full per-page flow becomes:

```
processPage(loans)
  └── enrichPage(loans)                          [EXISTING — unchanged]
        ├── getTlrSettings()                     (cached)
        ├── getTimeZone()                        (cached)
        ├── fetchItems()                         (chunked)
        ├── fetchUsers()                         (chunked)
        └── requestQueueLookup.lookupByLoanId()  (chunked)
              ↓ PreparedPage
  └── filterEligibleLoans(preparedPage)          [NEW PHASE]
        ├── For each loan: loanPolicyResolver → getLoanPolicy() (cached by policyId+ruleConditions)
        ├── Rule 1: automaticRenewal enabled? (silent skip if not)
        ├── Rule 2: within eligibility window? (silent skip if not, debug log)
        ├── Rule 3: no blocking hold/recall? (failure outcome + Circulation log if blocked)
        └── Returns AutomaticRenewalEligibilityResult:
              eligible            → List<Loan>
              holdRecallFailures  → Map<loanId, HttpFailure>
              ↓ FilteredPage (eligible loans + pre-recorded failures)
  └── renewPage(filteredPage)                    [EXISTING — unchanged]
        └── existing validations still run as final safeguard
```

### New files

| File | Responsibility |
|------|----------------|
| `BulkRenewalEligibilityFilter.java` | Pure eligibility decision logic. No I/O. Accepts a `PreparedPage` plus a map of resolved `LoanPolicy` objects; returns `AutomaticRenewalEligibilityResult`. |
| `AutomaticRenewalEligibilityResult.java` | Immutable record: `eligible: List<Loan>`, `holdRecallFailures: Map<String, HttpFailure>`. |

### Modified files

| File | Change |
|------|--------|
| `BulkRenewalPageProcessor.java` | Add `filterEligibleLoans()` step between `enrichPage()` and `renewPage()`; accept `EventPublisher` in constructor for failure log publishing. |
| `BulkRenewOpenLoansService.java` | Pass `EventPublisher` (from `Clients.eventPublishingService()`) into `BulkRenewalPageProcessor` constructor at `RealBackgroundJobRunner.create()`. |
| `BulkRenewalCachedDependencies.java` | Add `ruleMatches` cache keyed on `(itemTypeId, loanTypeId, locationId, patronGroupId)` to deduplicate circulation-rules-engine calls across loans. |
| `LoanPolicyRepository.java` | Update `lookupPolicyForBulkRenewal()` to consult the new `ruleMatches` cache before calling the rules engine. |
| `LoanAction.java` | Add `AUTOMATIC_RENEWAL_FAILED("automaticRenewalFailed")`. |
| `LogContextActionResolver.java` | Register `"automaticRenewalFailed"` → `"Automatic renewal failed"`. |
| `EventPublisher.java` | Add `publishAutomaticRenewalFailureLogEvent(Loan, String reason, String updatedByUserId)`. |

### Unchanged

- `BulkRenewalCachedDependencies` policy-level cache (already deduplicates by `policyId + ruleConditions`)
- `BulkRenewalRequestQueueLookup` (request queues already fetched during `enrichPage()`)
- `renewPage()` (existing validations remain as final safeguard)
- HTTP API (`POST /circulation/bulk-renew-open-loans`, `204 No Content`)

---

## Section 1: Eligibility Rules

Three rules are applied in order during the filter phase. Policy resolution (a cached
async call) is performed once per loan; the result feeds all three rules.

### Rule 1 — Policy enables automatic renewal

```
loanPolicy.isAutomaticRenewalEnabled() == true
```

- If `false`, or if the `renewalsPolicy` object is absent (the method returns `false`
  by default via `getBooleanProperty`): loan is **silently excluded** — no failure
  outcome, no log entry. Satisfies Scenarios 2 and 3.
- **Debug log:** `"filterEligibleLoans:: loanId={} excluded: automaticRenewal disabled or absent in policyId={}"`

### Rule 2 — Loan is within the eligibility window

```java
Integer firstAttemptDays = loanPolicy.getFirstAutomaticRenewalAttemptDaysBeforeDueDate();
// if null: treat as window not configured → exclude silently
long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(timeZone), loan.getDueDate().toLocalDate());
eligible if: daysUntilDue <= firstAttemptDays
```

- If `firstAutomaticRenewalAttemptDaysBeforeDueDate` is `null`: **silently excluded**.
- If `daysUntilDue > firstAttemptDays`: **excluded**.
- **Debug log in both cases:** `"filterEligibleLoans:: loanId={} excluded: outside eligibility window (daysUntilDue={}, firstAttemptDays={})"`
- Satisfies Scenario 4.

### Rule 3 — No blocking hold or recall

Uses the `RequestQueue` already fetched by `BulkRenewalRequestQueueLookup` (available
in `PreparedPage.requestQueuesByLoanId()`). No additional I/O.

**Recall blocks:**
```java
requestQueue.getRequests().stream()
    .filter(Request::isRecall)
    .filter(request -> request.isFor(loan))
    .findFirst()
    .isPresent()
```

**Hold blocks:**
```java
firstOpenRequest(requestQueue) is a hold
  AND loanPolicy.isHoldRequestRenewable() == false
```

If blocked: loan is **excluded with a failure outcome** and a Circulation log entry is
published. Satisfies Scenario 5.

The check reuses the same logic already present in `RenewalResource` lines 967–1003,
extracted into `BulkRenewalEligibilityFilter` as a pure method.

---

## Section 2: Circulation Log Entry for Hold/Recall Failures

For each loan excluded by Rule 3, one `LOAN` log record is written to the Circulation
log via `EventPublisher`.

### New `LoanAction` value

```java
AUTOMATIC_RENEWAL_FAILED("automaticRenewalFailed")
```

### New `LogContextActionResolver` entry

```java
loanLogActions.put(AUTOMATIC_RENEWAL_FAILED.getValue(), "Automatic renewal failed");
```

### New `EventPublisher` method

```java
public CompletableFuture<Result<Void>> publishAutomaticRenewalFailureLogEvent(
    Loan loan, String reason, String updatedByUserId) {

    logger.info("publishAutomaticRenewalFailureLogEvent:: loanId={}", loan.getId());
    var context = LoanLogContext.from(loan)
        .withAction(LogContextActionResolver.resolveAction(AUTOMATIC_RENEWAL_FAILED.getValue()))
        .withDescription(reason)
        .withUpdatedByUserId(updatedByUserId);
    return publishLogRecord(loan.getId(), context.asJson(), LOAN);
}
```

### Reason strings

These reuse the established error message text from the regular renewal flow:

| Cause | Reason string |
|-------|---------------|
| Active recall | `"items cannot be renewed when there is an active recall request"` |
| Active non-renewable hold | `"Items with this loan policy cannot be renewed when there is an active, pending hold request"` |

The failure is also stored in `BulkRenewalPageContext.failedRenewalsByLoanId` so it
appears alongside renewal-phase failures in existing counters and logs.

The `updatedByUserId` is `BulkRenewalPageContext.triggeringUserId()` (the Okapi
user-id from the original HTTP request headers, already carried through the page
context).

---

## Section 3: Policy Resolution Efficiency (Scenario 7)

### Problem

`lookupPolicyForBulkRenewal()` currently calls `circulationRulesProcessor
.getLoanPolicyAndMatch()` once per loan — one HTTP call to the rules engine per loan —
even when many loans share the same item type, loan type, location and patron group.
The existing `BulkRenewalCachedDependencies.getLoanPolicy()` only deduplicates the
*second* fetch (policy JSON), not the rules-engine call.

### Solution: circulation-rules result cache

Add a new cache in `BulkRenewalCachedDependencies`:

```java
// Key: the four parameters that identify a unique circulation-rules result
record RuleLookupCacheKey(
    String itemTypeId,
    String loanTypeId,
    String locationId,
    String patronGroupId
) {}

ConcurrentHashMap<RuleLookupCacheKey, CompletableFuture<Result<CirculationRuleMatch>>>
    ruleMatches = new ConcurrentHashMap<>();
```

```java
public CompletableFuture<Result<CirculationRuleMatch>> getCirculationRuleMatch(
    RuleLookupCacheKey key,
    Supplier<CompletableFuture<Result<CirculationRuleMatch>>> fetcher) {

    return ruleMatches.computeIfAbsent(key, ignored -> fetcher.get());
}
```

`lookupPolicyForBulkRenewal()` in `LoanPolicyRepository` builds the key from
`loan.getItem()` and `loan.getUser()` and delegates to `cachedDependencies
.getCirculationRuleMatch(key, () -> lookupPolicyId(loan))`.

### Verification

The requirement that circulation-rule determination is not invoked per loan must be
verified by an automated test. `BulkRenewOpenLoansServiceTest` or a new
`BulkRenewalCachedDependenciesTest` will stub the rules-engine call (or
`lookupPolicyForBulkRenewal`) and assert it is called **exactly once** when multiple
loans share the same item type/loan type/location/patron group combination, using
Mockito `verify(..., times(1))`.

---

## Section 4: `EventPublisher` wiring (Option A)

`EventPublisher` is passed into `BulkRenewalPageProcessor` as a constructor argument.
It is obtained from `Clients.eventPublishingService()` inside
`RealBackgroundJobRunner.create()`, where all other collaborators are already
constructed.

```java
// RealBackgroundJobRunner.create() — existing construction:
Clients clients = Clients.create(context, httpClient);
// ... existing repositories ...

// New:
BulkRenewalPageProcessor pageProcessor = new BulkRenewalPageProcessor(
    cachedDependencies,
    RealRunnerFactories.itemFetcher(itemRepository),
    RealRunnerFactories.userFetcher(userRepository),
    requestQueueLookup,
    RealRunnerFactories.loanPolicyResolver(cachedDependencies, loanPolicyRepository, requestRepository),
    (renewalContext, errorHandler) -> renewLoan(clients, renewResource, renewalContext,
        errorHandler, okapiPermissions, automatedPatronBlocksRepository),
    okapiPermissions,
    renewalRequest,
    clients.eventPublishingService()   // ← new argument
);
```

`BulkRenewalPageProcessor` stores it as `private final EventPublisher eventPublisher`
and calls `eventPublisher.publishAutomaticRenewalFailureLogEvent(loan, reason, triggeringUserId)`
for each hold/recall exclusion during the filter phase.

---

## Acceptance Criteria Coverage

| Scenario | Rule / mechanism |
|----------|-----------------|
| 1 — Auto-renewal enabled, in window, conditions pass → included | Rule 1 ✓ + Rule 2 ✓ + Rule 3 ✓ |
| 2 — Auto-renewal disabled → excluded silently | Rule 1 |
| 3 — No auto-renewal config → excluded silently | Rule 1 (default false) |
| 4 — Outside eligibility window → excluded silently | Rule 2 |
| 5 — Hold/recall blocks → excluded + failure outcome + Circ log entry | Rule 3 |
| 6 — Mixed-policy batch → only eligible included | All three rules applied per loan |
| 7 — Policy info obtained/reused across batch, rules not per-loan | `ruleMatches` cache + existing `loanPolicies` cache |

---

## Out of Scope (per ticket)

- Scheduling / stopping the job
- Integration with the new bulk patron-block API
- Persisted batch recovery and failover handling
- Patron notice dispatch for automatic renewal failures (deferred; Circulation log only for now)
- Re-enabling `storeLoanAndItem.updateLoanAndItemInStorage` (separate task)
- Re-enabling automated patron block checking (separate task)

---

## Automated Test Coverage Required

| Area | Test class |
|------|-----------|
| `BulkRenewalEligibilityFilter` unit tests — all three rules, boundary cases | `BulkRenewalEligibilityFilterTest` (new) |
| `BulkRenewalCachedDependencies` — rules cache deduplication (`times(1)` assert) | `BulkRenewalCachedDependenciesTest` (new or extend existing) |
| `BulkRenewalPageProcessor` — eligible-only loans reach `renewPage()` | `BulkRenewalPageProcessorTest` (extend) |
| `EventPublisher.publishAutomaticRenewalFailureLogEvent` | `EventPublisherTest` (extend) |
| `LoanAction.AUTOMATIC_RENEWAL_FAILED` value and `LogContextActionResolver` mapping | inline in existing unit tests |
| Mixed-policy batch end-to-end (Scenario 6) | `BulkRenewOpenLoansServiceTest` (extend) |
