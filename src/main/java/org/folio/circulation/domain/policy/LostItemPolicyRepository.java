package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

public class LostItemPolicyRepository extends CirculationPolicyRepository<LostItemPolicy> {

  public LostItemPolicyRepository(Clients clients) {
    super(clients.circulationLostItemRules(), clients.lostItemPoliciesStorage());
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> lookupLoanPolicy(
          LoanAndRelatedRecords relatedRecords) {

    return Result.of(relatedRecords::getLoan)
            .combineAfter(this::lookupPolicy, Loan::withLostItemPolicy)
            .thenApply(mapResult(relatedRecords::withLoan));
  }

  @Override
  public CompletableFuture<Result<LostItemPolicy>> lookupPolicy(Loan loan) {
    return super.lookupPolicy(loan);
  }


  @Override
  protected String getPolicyNotFoundErrorMessage(String policyId) {
    return String.format("Loan policy %s could not be found, please check circulation rules", policyId);
  }

  @Override
  protected Result<LostItemPolicy> toPolicy(JsonObject representation) {
    return succeeded(new LostItemPolicy(representation));
  }

  @Override
  protected String fetchPolicyId(JsonObject jsonObject) {
    return jsonObject.getString("lostItemPolicyId");
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findLoanPoliciesForLoans(MultipleRecords<Loan> multipleLoans) {
    Collection<Loan> loans = multipleLoans.getRecords();

    return getLoanPolicies(loans)
      .thenApply(r -> r.map(loanPolicies -> multipleLoans.mapRecords(
        loan -> loan.withLostItemPolicy(loanPolicies.getOrDefault(
          loan.getLostItemPolicyId(), LostItemPolicy.unknown(loan.getLostItemPolicyId())))))
      );
  }

  private CompletableFuture<Result<Map<String, LostItemPolicy>>> getLoanPolicies(Collection<Loan> loans) {
    final Collection<String> loansToFetch = loans.stream()
      .map(Loan::getLostItemPolicyId)
      .filter(Objects::nonNull)
      .distinct()
      .collect(Collectors.toSet());

    final MultipleRecordFetcher<LostItemPolicy> fetcher = createLoanPoliciesFetcher();

    return fetcher.findByIds(loansToFetch)
      .thenApply(mapResult(r -> r.toMap(LostItemPolicy::getId)));
  }

  private MultipleRecordFetcher<LostItemPolicy> createLoanPoliciesFetcher() {
    return new MultipleRecordFetcher<>(policyStorageClient, "lostItemFeePolicies", LostItemPolicy::from);
  }
}
