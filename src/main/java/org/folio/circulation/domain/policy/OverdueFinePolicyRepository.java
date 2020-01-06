package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

public class OverdueFinePolicyRepository extends CirculationPolicyRepository<OverdueFinePolicy> {

  public OverdueFinePolicyRepository(Clients clients) {
    super(clients.circulationOverdueFineRules(), clients.overdueFinesPoliciesStorage());
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> lookupOverdueFinePolicy(
    LoanAndRelatedRecords relatedRecords) {

    return Result.of(relatedRecords::getLoan)
      .combineAfter(this::lookupPolicy, Loan::withOverdueFinePolicy)
      .thenApply(mapResult(relatedRecords::withLoan));
  }

  @Override
  public CompletableFuture<Result<OverdueFinePolicy>> lookupPolicy(Loan loan) {
    return super.lookupPolicy(loan);
  }


  @Override
  protected String getPolicyNotFoundErrorMessage(String policyId) {
    return String.format("Overdue fine policy %s could not be found," +
      " please check circulation rules", policyId);
  }

  @Override
  protected Result<OverdueFinePolicy> toPolicy(JsonObject representation) {
    return succeeded(OverdueFinePolicy.from((representation)));
  }

  @Override
  protected String fetchPolicyId(JsonObject jsonObject) {
    return jsonObject.getString("overdueFinePolicyId");
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>>
    findOverdueFinePoliciesForLoans(MultipleRecords<Loan> multipleLoans) {

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

    final MultipleRecordFetcher<OverdueFinePolicy> fetcher = createOverdueFinePoliciesFetcher();

    return fetcher.findByIds(loansToFetch)
      .thenApply(mapResult(r -> r.toMap(OverdueFinePolicy::getId)));
  }

  private MultipleRecordFetcher<OverdueFinePolicy> createOverdueFinePoliciesFetcher() {
    return new MultipleRecordFetcher<>(policyStorageClient,
      "overdueFinePolicies", OverdueFinePolicy::from);
  }

  public CompletableFuture<Result<Loan>> findOverdueFinePolicyForLoan(Result<Loan> loanResult) {
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
}
