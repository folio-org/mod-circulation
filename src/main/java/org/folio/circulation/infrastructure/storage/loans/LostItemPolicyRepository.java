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
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.infrastructure.storage.CirculationPolicyRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class LostItemPolicyRepository extends CirculationPolicyRepository<LostItemPolicy> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public LostItemPolicyRepository(Clients clients) {
    super(clients.lostItemPoliciesStorage(), clients);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> lookupLostItemPolicy(
          LoanAndRelatedRecords relatedRecords) {

    log.debug("lookupLostItemPolicy:: parameters relatedRecords: {}", relatedRecords);

    return Result.of(relatedRecords::getLoan)
      .combineAfter(this::lookupPolicy, Loan::withLostItemPolicy)
      .thenApply(mapResult(relatedRecords::withLoan));
  }

  @Override
  protected String getPolicyNotFoundErrorMessage(String policyId) {
    return String.format("Lost item policy %s could not be found, please check circulation rules", policyId);
  }

  @Override
  protected Result<LostItemPolicy> toPolicy(
    JsonObject representation, AppliedRuleConditions ruleConditionsEntity) {

    return succeeded(LostItemPolicy.from(representation));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findLostItemPoliciesForLoans(
    MultipleRecords<Loan> multipleLoans) {

    log.debug("findLostItemPoliciesForLoans:: parameters multipleLoans: {}", () -> multipleRecordsAsString(multipleLoans));

    Collection<Loan> loans = multipleLoans.getRecords();

    return getLostItemPolicies(loans)
      .thenApply(r -> r.map(loanPolicies -> multipleLoans.mapRecords(
        loan -> loan.withLostItemPolicy(loanPolicies.getOrDefault(
          loan.getLostItemPolicyId(), LostItemPolicy.unknown(loan.getLostItemPolicyId())))))
      );
  }

  private CompletableFuture<Result<Map<String, LostItemPolicy>>> getLostItemPolicies(
    Collection<Loan> loans) {

    final Collection<String> loansToFetch = loans.stream()
      .map(Loan::getLostItemPolicyId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    final FindWithMultipleCqlIndexValues<LostItemPolicy> fetcher = createLostItemPoliciesFetcher();

    return fetcher.findByIds(loansToFetch)
      .thenApply(mapResult(r -> r.toMap(LostItemPolicy::getId)));
  }

  private FindWithMultipleCqlIndexValues<LostItemPolicy> createLostItemPoliciesFetcher() {
    return findWithMultipleCqlIndexValues(policyStorageClient, "lostItemFeePolicies",
      LostItemPolicy::from);
  }

  public CompletableFuture<Result<Loan>> findLostItemPolicyForLoan(
    Result<Loan> loanResult) {

    log.debug("findLostItemPolicyForLoan:: parameters loanResult: {}", () -> resultAsString(loanResult));

    return loanResult.after(loan ->
      getLostItemPolicyById(loan.getLostItemPolicyId())
        .thenApply(result -> result.map(loan::withLostItemPolicy)));
  }

  public CompletableFuture<Result<LostItemPolicy>> getLostItemPolicyById(
    String lostItemPolicyId) {

    log.debug("getLostItemPolicyById:: parameters lostItemPolicyId: {}", lostItemPolicyId);

    if (isNull(lostItemPolicyId)) {
      return ofAsync(() -> LostItemPolicy.unknown(null));
    }

    return FetchSingleRecord.<LostItemPolicy>forRecord("lostItemFeePolicies")
      .using(policyStorageClient)
      .mapTo(LostItemPolicy::from)
      .whenNotFound(succeeded(LostItemPolicy.unknown(lostItemPolicyId)))
      .fetch(lostItemPolicyId);
  }

  @Override
  protected CompletableFuture<Result<CirculationRuleMatch>> getPolicyAndMatch(
    RulesExecutionParameters rulesExecutionParameters) {

    return circulationRulesProcessor.getLostItemPolicyAndMatch(rulesExecutionParameters);
  }
}
