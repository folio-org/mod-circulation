package org.folio.circulation.infrastructure.storage.loans;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.infrastructure.storage.CirculationPolicyRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class OverdueFinePolicyRepository extends CirculationPolicyRepository<OverdueFinePolicy> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public OverdueFinePolicyRepository(Clients clients) {
    super(clients.overdueFinesPoliciesStorage(), clients);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> lookupOverdueFinePolicy(
    LoanAndRelatedRecords relatedRecords) {
    log.debug("lookupOverdueFinePolicy:: parameters relatedRecords: {}", relatedRecords);

    return Result.of(relatedRecords::getLoan)
      .combineAfter(this::lookupPolicy, Loan::withOverdueFinePolicy)
      .thenApply(mapResult(relatedRecords::withLoan));
  }

  @Override
  protected String getPolicyNotFoundErrorMessage(String policyId) {
    return String.format("Overdue fine policy %s could not be found," +
      " please check circulation rules", policyId);
  }

  @Override
  protected Result<OverdueFinePolicy> toPolicy(
    JsonObject representation, AppliedRuleConditions ruleConditionsEntity) {

    return succeeded(OverdueFinePolicy.from((representation)));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>>
    findOverdueFinePoliciesForLoans(MultipleRecords<Loan> multipleLoans) {

    log.debug("findOverdueFinePoliciesForLoans:: parameters multipleLoans: {}", () ->  multipleRecordsAsString(multipleLoans));

    Collection<Loan> loans = multipleLoans.getRecords();

    return getOverdueFinePolicies(loans)
      .thenApply(r -> r.map(loanPolicies -> multipleLoans.mapRecords(
        loan -> loan.withOverdueFinePolicy(loanPolicies.getOrDefault(
          loan.getOverdueFinePolicyId(),
          OverdueFinePolicy.unknown(loan.getOverdueFinePolicyId())))))
      );
  }

  private CompletableFuture<Result<Map<String, OverdueFinePolicy>>>
    getOverdueFinePolicies(Collection<Loan> loans) {

    final Collection<String> loansToFetch = loans.stream()
      .map(Loan::getOverdueFinePolicyId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    final FindWithMultipleCqlIndexValues<OverdueFinePolicy> fetcher = createOverdueFinePoliciesFetcher();

    return fetcher.findByIds(loansToFetch)
      .thenApply(mapResult(r -> r.toMap(OverdueFinePolicy::getId)));
  }

  private FindWithMultipleCqlIndexValues<OverdueFinePolicy> createOverdueFinePoliciesFetcher() {
    return findWithMultipleCqlIndexValues(policyStorageClient, "overdueFinePolicies",
      OverdueFinePolicy::from);
  }

  public CompletableFuture<Result<Loan>> findOverdueFinePolicyForLoan(Result<Loan> loanResult) {
    log.debug("findOverdueFinePolicyForLoan:: parameters loanResult: {}", () -> resultAsString(loanResult));
    return loanResult.after(loan ->
      getOverdueFinePolicyById(loan.getOverdueFinePolicyId())
        .thenApply(result -> result.map(loan::withOverdueFinePolicy)));
  }

  private CompletableFuture<Result<OverdueFinePolicy>> getOverdueFinePolicyById(
    String overdueFinePolicyId) {

    if (isNull(overdueFinePolicyId)) {
      return ofAsync(() -> OverdueFinePolicy.unknown(null));
    }

    return FetchSingleRecord.<OverdueFinePolicy>forRecord("overdueFinePolicies")
      .using(policyStorageClient)
      .mapTo(OverdueFinePolicy::from)
      .whenNotFound(succeeded(OverdueFinePolicy.unknown(overdueFinePolicyId)))
      .fetch(overdueFinePolicyId);
  }

  @Override
  protected CompletableFuture<Result<CirculationRuleMatch>> getPolicyAndMatch(
    RulesExecutionParameters rulesExecutionParameters) {

    return circulationRulesProcessor.getOverduePolicyAndMatch(rulesExecutionParameters);
  }
}
