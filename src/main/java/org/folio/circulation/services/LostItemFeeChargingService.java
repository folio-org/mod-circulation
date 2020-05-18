package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.LoanAction.CLOSED_LOAN;
import static org.folio.circulation.support.Result.combineAll;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.FeeFineOwnerRepository;
import org.folio.circulation.domain.FeeFineRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LostItemFeeChargingService {
  private static final Logger log = LoggerFactory.getLogger(LostItemFeeChargingService.class);
  private static final List<String> FEE_TYPES_TO_RETRIEVE = Arrays.asList(
    LOST_ITEM_FEE_TYPE, LOST_ITEM_PROCESSING_FEE_TYPE);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;
  private final FeeFineRepository feeFineRepository;
  private final FeeFineService feeFineService;

  public LostItemFeeChargingService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.feeFineService = new FeeFineService(clients);
  }

  public CompletableFuture<Result<Loan>> chargeLostItemFees(
    Loan loan, DeclareItemLostRequest request, String staffUserId) {

    final ReferenceDataContext referenceDataContext = new ReferenceDataContext(
      loan, request, staffUserId);

    return lostItemPolicyRepository.getLostItemPolicyById(loan.getLostItemPolicyId())
      .thenApply(result -> result.map(referenceDataContext::withLostItemPolicy))
      .thenCompose(refDataResult -> refDataResult.after(referenceData -> {
        if (shouldCloseLoan(referenceData.lostItemPolicy)) {
          log.debug("Loan [{}] can be closed because no fee will be charged", loan.getId());
          return closeLoan(loan);
        }

        return fetchFeeFineOwner(referenceData)
          .thenApply(this::refuseWhenFeeFineOwnerIsNotFound)
          .thenComposeAsync(this::fetchFeeFineTypes)
          .thenApply(this::buildAccountsAndActions)
          .thenCompose(r -> r.after(feeFineService::createAccounts))
          .thenApply(r -> r.map(notUsed -> loan));
      }));
  }


  private CompletableFuture<Result<Loan>> closeLoan(Loan loan) {
    return completedFuture(succeeded(loan.closeLoan(CLOSED_LOAN)));
  }

  private boolean shouldCloseLoan(LostItemPolicy policy) {
    return !policy.getProcessingFee().isChargeable()
      && !policy.getSetCostFee().isChargeable()
      && !policy.getActualCostFee().isChargeable();
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchFeeFineTypes(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.combineAfter(
      context -> feeFineRepository.getAutomaticFeeFines(FEE_TYPES_TO_RETRIEVE),
      ReferenceDataContext::withFeeFines);
  }

  private Result<List<CreateAccountCommand>> buildAccountsAndActions(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.next(context -> {
      final LostItemPolicy policy = context.lostItemPolicy;
      final List<Result<CreateAccountCommand>> accountsToCreate = new ArrayList<>();
      final Collection<FeeFine> feeFines = context.feeFines;

      if (policy.getSetCostFee().isChargeable()) {
        log.debug("Charging lost item fee");

        final Result<CreateAccountCommand> lostItemFeeResult =
          getFeeFineOfType(feeFines, LOST_ITEM_FEE_TYPE)
            .map(createAccountCreation(context, policy.getSetCostFee()));

        accountsToCreate.add(lostItemFeeResult);
      }

      if (policy.getProcessingFee().isChargeable()) {
        log.debug("Charging lost item processing fee");

        final Result<CreateAccountCommand> processingFeeResult =
          getFeeFineOfType(feeFines, LOST_ITEM_PROCESSING_FEE_TYPE)
            .map(createAccountCreation(context, policy.getProcessingFee()));

        accountsToCreate.add(processingFeeResult);
      }

      log.debug("Total accounts created {}", accountsToCreate.size());
      return combineAll(accountsToCreate);
    });
  }

  private UUID getOwnerServicePoint(Loan loan) {
    return loan.getItem().getLocation().getPrimaryServicePointId();
  }

  private Function<FeeFine, CreateAccountCommand> createAccountCreation(
    ReferenceDataContext context, AutomaticallyChargeableFee fee) {

    return feeFine -> CreateAccountCommand.builder()
      .withAmount(fee.getAmount())
      .withCurrentServicePointId(context.request.getServicePointId())
      .withStaffUserId(context.staffUserId)
      .withFeeFine(feeFine)
      .withFeeFineOwner(context.feeFineOwner)
      .withLoan(context.loan)
      .withItem(context.loan.getItem())
      .build();
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchFeeFineOwner(ReferenceDataContext referenceData) {
    final String servicePointId = getOwnerServicePoint(referenceData.loan).toString();

    return feeFineOwnerRepository.findOwnerForServicePoint(servicePointId)
      .thenApply(ownerResult -> ownerResult.map(referenceData::withFeeFineOwner));
  }

  private Result<FeeFine> getFeeFineOfType(Collection<FeeFine> feeFines, String type) {
    return feeFines.stream()
      .filter(feeFine -> feeFine.getFeeFineType().equals(type))
      .findFirst()
      .map(Result::succeeded)
      .orElse(createFeeFineNotFoundResult(type));
  }

  private Result<FeeFine> createFeeFineNotFoundResult(String type) {
    return failed(singleValidationError("Expected automated fee of type " + type,
      "feeFineType", type));
  }

  private Result<ReferenceDataContext> refuseWhenFeeFineOwnerIsNotFound(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.failWhen(
      context -> succeeded(context.feeFineOwner == null),
      context -> singleValidationError("No fee/fine owner found for item's effective location",
        "servicePointId", getOwnerServicePoint(context.loan).toString()));
  }

  private static final class ReferenceDataContext {
    private final Loan loan;
    private final DeclareItemLostRequest request;
    private final String staffUserId;

    private LostItemPolicy lostItemPolicy;
    private FeeFineOwner feeFineOwner;
    private Collection<FeeFine> feeFines;

    public ReferenceDataContext(Loan loan, DeclareItemLostRequest request, String staffUserId) {
      this.loan = loan;
      this.request = request;
      this.staffUserId = staffUserId;
    }

    public ReferenceDataContext withLostItemPolicy(LostItemPolicy lostItemPolicy) {
      this.lostItemPolicy = lostItemPolicy;
      return this;
    }

    public ReferenceDataContext withFeeFineOwner(FeeFineOwner feeFineOwner) {
      this.feeFineOwner = feeFineOwner;
      return this;
    }

    public ReferenceDataContext withFeeFines(Collection<FeeFine> feeFines) {
      this.feeFines = feeFines;
      return this;
    }
  }
}
