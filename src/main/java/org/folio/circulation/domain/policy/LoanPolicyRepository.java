package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class LoanPolicyRepository extends CirculationPolicyRepository<LoanPolicy> {

  private final CollectionResourceClient fixedDueDateSchedulesStorageClient;

  public LoanPolicyRepository(Clients clients) {
    super(clients.circulationLoanRules(), clients.loanPoliciesStorage());
    this.fixedDueDateSchedulesStorageClient = clients.fixedDueDateSchedules();
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> lookupLoanPolicy(
    LoanAndRelatedRecords relatedRecords) {

    return lookupPolicy(relatedRecords.getLoan())
      .thenApply(result -> result.map(relatedRecords::withLoanPolicy));
  }

  @Override
  public CompletableFuture<Result<LoanPolicy>> lookupPolicy(Loan loan) {
    return super.lookupPolicy(loan)
      .thenComposeAsync(r -> r.after(this::lookupSchedules));
  }

  private CompletableFuture<Result<LoanPolicy>> lookupSchedules(LoanPolicy loanPolicy) {
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

    final MultipleRecordFetcher<FixedDueDateSchedules> fetcher
      = new MultipleRecordFetcher<>(fixedDueDateSchedulesStorageClient,
        "fixedDueDateSchedules", FixedDueDateSchedules::from);

    return fetcher.findByIds(schedulesIds)
      .thenApply(mapResult(schedules -> schedules.toMap(FixedDueDateSchedules::getId)));
  }

  @Override
  protected String getPolicyNotFoundErrorMessage(String policyId) {
    return String.format("Loan policy %s could not be found, please check circulation rules", policyId);
  }

  @Override
  protected Result<LoanPolicy> toPolicy(JsonObject representation) {
    return succeeded(new LoanPolicy(representation,
      new NoFixedDueDateSchedules(), new NoFixedDueDateSchedules()));
  }

  @Override
  protected String fetchPolicyId(JsonObject jsonObject) {
    return jsonObject.getString("loanPolicyId");
  }
}
