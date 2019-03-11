package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.circulation.support.CqlHelper.multipleRecordsCqlQuery;
import static org.folio.circulation.support.HttpResult.succeeded;

public class LoanPolicyRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CirculationRulesClient circulationLoanRulesClient;
  private final CollectionResourceClient loanPoliciesStorageClient;
  private final CollectionResourceClient fixedDueDateSchedulesStorageClient;

  public LoanPolicyRepository(Clients clients) {
    circulationLoanRulesClient = clients.circulationLoanRules();
    loanPoliciesStorageClient = clients.loanPoliciesStorage();
    fixedDueDateSchedulesStorageClient = clients.fixedDueDateSchedules();
  }

  public CompletableFuture<HttpResult<LoanPolicy>> lookupLoanPolicy(Loan loan) {
    return lookupLoanPolicy(loan.getItem(), loan.getUser());
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> lookupLoanPolicy(
    LoanAndRelatedRecords relatedRecords) {

    return lookupLoanPolicy(relatedRecords.getLoan())
      .thenApply(result -> result.map(relatedRecords::withLoanPolicy));
  }

  private CompletableFuture<HttpResult<LoanPolicy>> lookupLoanPolicy(
    Item item,
    User user) {

    return lookupLoanPolicyId(item, user)
      .thenComposeAsync(r -> r.after(this::lookupLoanPolicy))
      .thenApply(result -> result.map(this::toLoanPolicy))
      .thenComposeAsync(r -> r.after(this::lookupSchedules));
  }

  private LoanPolicy toLoanPolicy(JsonObject representation) {
    return new LoanPolicy(representation,
      new NoFixedDueDateSchedules(), new NoFixedDueDateSchedules());
  }

  private CompletableFuture<HttpResult<LoanPolicy>> lookupSchedules(LoanPolicy loanPolicy) {
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

  private CompletableFuture<HttpResult<Map<String, FixedDueDateSchedules>>> getSchedules(
    Collection<String> schedulesIds) {

    String schedulesQuery = multipleRecordsCqlQuery(schedulesIds);

    return fixedDueDateSchedulesStorageClient.getMany(schedulesQuery,
      schedulesIds.size(), 0)
      .thenApply(schedulesResponse -> {
        if (schedulesResponse.getStatusCode() != 200) {
          return HttpResult.failed(new ServerErrorFailure(
            String.format("Fixed due date schedules request (%s) failed %s: %s",
              schedulesQuery, schedulesResponse.getStatusCode(),
              schedulesResponse.getBody())));
        }

        List<JsonObject> schedules = JsonArrayHelper.toList(
          schedulesResponse.getJson().getJsonArray("fixedDueDateSchedules"));

        return succeeded(schedules.stream()
          .collect(Collectors.toMap(
            s -> s.getString("id"),
            FixedDueDateSchedules::from)));
      });
  }

  private CompletableFuture<HttpResult<JsonObject>> lookupLoanPolicy(
    String loanPolicyId) {

    return SingleRecordFetcher.json(loanPoliciesStorageClient, "loan policy",
      response -> HttpResult.failed(new ServerErrorFailure(
        String.format("Loan policy %s could not be found, please check circulation rules", loanPolicyId))))
      .fetch(loanPolicyId);
  }

  private CompletableFuture<HttpResult<String>> lookupLoanPolicyId(
    Item item,
    User user) {

    CompletableFuture<HttpResult<String>> findLoanPolicyCompleted
      = new CompletableFuture<>();

    if (item.isNotFound()) {
      return CompletableFuture.completedFuture(HttpResult.failed(
        new ServerErrorFailure("Unable to apply circulation rules for unknown item")));
    }

    if (item.doesNotHaveHolding()) {
      return CompletableFuture.completedFuture(HttpResult.failed(
        new ServerErrorFailure("Unable to apply circulation rules for unknown holding")));
    }

    String loanTypeId = item.determineLoanTypeForItem();
    String locationId = item.getLocationId();

    String materialTypeId = item.getMaterialTypeId();

    String patronGroupId = user.getPatronGroupId();

    CompletableFuture<Response> circulationRulesResponse = new CompletableFuture<>();

    log.info(
      "Applying circulation rules for material type: {}, patron group: {}, loan type: {}, location: {}",
      materialTypeId, patronGroupId, loanTypeId, locationId);

      circulationLoanRulesClient.applyRules(loanTypeId, locationId, materialTypeId,
      patronGroupId, ResponseHandler.any(circulationRulesResponse));

    circulationRulesResponse.thenAcceptAsync(response -> {
      if (response.getStatusCode() == 404) {
        findLoanPolicyCompleted.complete(HttpResult.failed(
          new ServerErrorFailure("Unable to apply circulation rules")));
      } else if (response.getStatusCode() != 200) {
        findLoanPolicyCompleted.complete(HttpResult.failed(
          new ForwardOnFailure(response)));
      } else {
        String policyId = response.getJson().getString("loanPolicyId");
        findLoanPolicyCompleted.complete(succeeded(policyId));
      }
    });

    return findLoanPolicyCompleted;
  }

}
