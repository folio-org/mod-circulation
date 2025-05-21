package org.folio.circulation.infrastructure.storage.loans;

import static java.util.Objects.isNull;
import static org.folio.circulation.domain.policy.LoanPolicy.unknown;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.policy.FixedDueDateSchedules;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.NoFixedDueDateSchedules;
import org.folio.circulation.infrastructure.storage.CirculationPolicyRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class LoanPolicyRepository extends CirculationPolicyRepository<LoanPolicy> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final GetManyRecordsClient fixedDueDateSchedulesStorageClient;

  public LoanPolicyRepository(Clients clients) {
    super(clients.loanPoliciesStorage(), clients);
    this.fixedDueDateSchedulesStorageClient = clients.fixedDueDateSchedules();
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> lookupLoanPolicy(
    LoanAndRelatedRecords relatedRecords) {

    log.debug("lookupLoanPolicy:: parameters: relatedRecords: {}", relatedRecords);
    if (relatedRecords.getLoan() == null || relatedRecords.getLoan().getUser() == null) {
      log.info("lookupLoanPolicy:: loan or user is null");
      return ofAsync(() -> relatedRecords);
    }
    return getLoanPolicy(relatedRecords)
      .thenApply(result -> result.map(loanPolicy ->
        relatedRecords.withLoan(relatedRecords.getLoan().withLoanPolicy(loanPolicy))
      ));
  }

  public CompletableFuture<Result<Loan>> findPolicyForLoan(Result<Loan> loanResult) {
    log.debug("findPolicyForLoan:: parameters loanResult: {}", () -> resultAsString(loanResult));
    return loanResult.after(loan ->
      getLoanPolicyById(loan.getLoanPolicyId())
      .thenApply(result -> result.map(loan::withLoanPolicy)));
  }

  public CompletableFuture<Result<Loan>> findPolicyForLoan(Loan loan) {
    log.debug("findPolicyForLoan:: parameters loan: {}", loan);
    return getLoanPolicyById(loan.getLoanPolicyId())
        .thenApply(result -> result.map(loan::withLoanPolicy));
  }

  private CompletableFuture<Result<LoanPolicy>> getLoanPolicy(LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getForceLoanPolicyId() != null) {
      log.info("getLoanPolicy:: forceLoanPolicyId is set, getting Loan Policy by ID: {}",
              relatedRecords.getForceLoanPolicyId());
      return getLoanPolicyById(relatedRecords.getForceLoanPolicyId());
    }
    return lookupPolicy(relatedRecords.getLoan());
  }

  public CompletableFuture<Result<LoanPolicy>> getLoanPolicyById(String loanPolicyId) {
    log.debug("getLoanPolicyById:: parameters loanPolicyId: {}", loanPolicyId);
    if (isNull(loanPolicyId)) {
      log.info("getLoanPolicyById:: loanPolicy id is null");
      return ofAsync(() -> unknown(null));
    }

    return FetchSingleRecord.<LoanPolicy>forRecord("loan policy")
      .using(policyStorageClient)
      .mapTo(LoanPolicy::from)
      .whenNotFound(succeeded(unknown(loanPolicyId)))
      .fetch(loanPolicyId);
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findLoanPoliciesForLoans(MultipleRecords<Loan> multipleLoans) {
    log.debug("findLoanPoliciesForLoans:: parameters multipleLoans: {}",
      () -> multipleRecordsAsString(multipleLoans));
    Collection<Loan> loans = multipleLoans.getRecords();

    log.info("findLoanPoliciesForLoans:: loans: {}", loans.size());

    return getLoanPolicies(loans)
      .thenApply(r -> r.map(loanPolicies -> multipleLoans.mapRecords(
        loan -> loan.withLoanPolicy(loanPolicies.getOrDefault(
          loan.getLoanPolicyId(), unknown(loan.getLoanPolicyId())))))
      );
  }

  private CompletableFuture<Result<Map<String, LoanPolicy>>> getLoanPolicies(Collection<Loan> loans) {
    log.debug("getLoanPolicies:: parameters loans: {}", () -> collectionAsString(loans));
    final Collection<String> loansToFetch = loans.stream()
            .map(Loan::getLoanPolicyId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    final FindWithMultipleCqlIndexValues<LoanPolicy> fetcher = createLoanPoliciesFetcher();

    return fetcher.findByIds(loansToFetch)
      .thenApply(mapResult(r -> r.toMap(LoanPolicy::getId)));
  }

  private FindWithMultipleCqlIndexValues<LoanPolicy> createLoanPoliciesFetcher() {
    return findWithMultipleCqlIndexValues(policyStorageClient, "loanPolicies",
      LoanPolicy::from);
  }

  @Override
  public CompletableFuture<Result<LoanPolicy>> lookupPolicy(Loan loan) {
    log.debug("lookupPolicy:: parameters loan: {}", loan);
    return super.lookupPolicy(loan)
      .thenComposeAsync(r -> r.after(this::lookupSchedules));
  }

  private CompletableFuture<Result<LoanPolicy>> lookupSchedules(LoanPolicy loanPolicy) {
    log.debug("lookupSchedules:: parameters loanPolicy: {}", loanPolicy);
    List<String> scheduleIds = new ArrayList<>();

    final String loanScheduleId = loanPolicy.getLoansFixedDueDateScheduleId();
    final String alternateRenewalsSchedulesId = loanPolicy.getAlternateRenewalsFixedDueDateScheduleId();

    if (loanScheduleId != null) {
      scheduleIds.add(loanScheduleId);
    }

    if (alternateRenewalsSchedulesId != null) {
      scheduleIds.add(alternateRenewalsSchedulesId);
    }

    if (scheduleIds.isEmpty()) {
      log.info("lookupSchedules:: no schedules to lookup");
      return CompletableFuture.completedFuture(succeeded(loanPolicy));
    }

    return getSchedules(scheduleIds)
      .thenApply(r -> r.next(schedules -> {
        final FixedDueDateSchedules loanSchedule = schedules.getOrDefault(
          loanScheduleId, new NoFixedDueDateSchedules());

        final FixedDueDateSchedules renewalSchedule = schedules.getOrDefault(
          alternateRenewalsSchedulesId, new NoFixedDueDateSchedules());

        return succeeded(loanPolicy
          .withDueDateSchedules(loanSchedule)
          .withAlternateRenewalSchedules(renewalSchedule));
      }));
  }

  private CompletableFuture<Result<Map<String, FixedDueDateSchedules>>> getSchedules(
    Collection<String> schedulesIds) {

    log.debug("getSchedules:: parameters schedulesIds: {}", () -> collectionAsString(schedulesIds));

    final FindWithMultipleCqlIndexValues<FixedDueDateSchedules> fetcher
      = findWithMultipleCqlIndexValues(fixedDueDateSchedulesStorageClient,
        "fixedDueDateSchedules", FixedDueDateSchedules::from);

    return fetcher.findByIds(schedulesIds)
      .thenApply(mapResult(schedules -> schedules.toMap(FixedDueDateSchedules::getId)));
  }

  @Override
  protected String getPolicyNotFoundErrorMessage(String policyId) {
    return String.format("Loan policy %s could not be found, please check circulation rules", policyId);
  }

  @Override
  protected Result<LoanPolicy> toPolicy(JsonObject representation, AppliedRuleConditions ruleConditionsEntity) {
    return succeeded(new LoanPolicy(representation,
      new NoFixedDueDateSchedules(), new NoFixedDueDateSchedules(), ruleConditionsEntity));
  }

  @Override
  protected CompletableFuture<Result<CirculationRuleMatch>> getPolicyAndMatch(
    RulesExecutionParameters rulesExecutionParameters) {

    return circulationRulesProcessor.getLoanPolicyAndMatch(rulesExecutionParameters);
  }
}
