package org.folio.circulation.services;

import static java.math.BigDecimal.ZERO;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.LoanAction.CLOSED_LOAN;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.SET_COST;
import static org.folio.circulation.support.Result.combineAll;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineAccountAndAction;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.FeeFineOwnerRepository;
import org.folio.circulation.domain.FeeFineRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.policy.lostitem.ChargeAmount;
import org.folio.circulation.domain.policy.lostitem.ItemFee;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
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
  private final ServicePointRepository servicePointRepository;
  private final UserRepository userRepository;
  private final FeeFineService feeFineService;

  public LostItemFeeChargingService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.servicePointRepository = new ServicePointRepository(clients);
    this.userRepository = new UserRepository(clients);
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
          .thenComposeAsync(this::fetchServicePoint)
          .thenComposeAsync(this::fetchStaffUser)
          .thenApply(this::buildAccountsAndActions)
          .thenCompose(r -> r.after(feeFineService::createAccountsAndActions))
          .thenApply(r -> r.map(notUsed -> loan));
      }));
  }


  private CompletableFuture<Result<Loan>> closeLoan(Loan loan) {
    return completedFuture(succeeded(loan.closeLoan(CLOSED_LOAN)));
  }

  private boolean shouldCloseLoan(LostItemPolicy policy) {
    // Can close a loan if set cost is used, actual cost requires manual processing
    return !shouldChargeProcessingFee(policy)
      && (itemChargeFeeIsNotDefined(policy) || setCostItemFeeHasZeroValue(policy));
  }

  private boolean itemChargeFeeIsNotDefined(LostItemPolicy policy) {
    return !policy.getChargeAmountItem().isPresent();
  }

  private boolean setCostItemFeeHasZeroValue(LostItemPolicy policy) {
    return policy.getChargeAmountItem()
      .filter(chargeAmount -> chargeAmount.getChargeType() == SET_COST)
      .filter(chargeAmount -> !isGreaterThanZero(chargeAmount.getAmount()))
      .isPresent();
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchStaffUser(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.combineAfter(
      context -> userRepository.getUser(context.staffUserId),
      ReferenceDataContext::withStaffUser);
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchServicePoint(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.combineAfter(
      context -> servicePointRepository.getServicePointById(context.request.getServicePointId()),
      ReferenceDataContext::withServicePoint);
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchFeeFineTypes(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.combineAfter(
      context -> feeFineRepository.getAutomaticFeeFines(FEE_TYPES_TO_RETRIEVE),
      ReferenceDataContext::withFeeFines);
  }

  private Result<List<FeeFineAccountAndAction>> buildAccountsAndActions(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.next(context -> {
      final LostItemPolicy policy = context.lostItemPolicy;
      final List<Result<FeeFineAccountAndAction>> accountsToCreate = new ArrayList<>();
      final Collection<FeeFine> feeFines = context.feeFines;

      if (shouldChargeItemFee(policy)) {
        log.debug("Charging lost item fee");

        final BigDecimal feeAmount = policy.getChargeAmountItem()
          .map(ChargeAmount::getAmount).orElse(BigDecimal.ZERO);

        final Result<FeeFineAccountAndAction> lostItemFeeResult =
          getFeeFineOfType(feeFines, LOST_ITEM_FEE_TYPE)
            .map(createAccountAndAction(context, feeAmount));

        accountsToCreate.add(lostItemFeeResult);
      }

      if (shouldChargeProcessingFee(policy)) {
        log.debug("Charging lost item processing fee");

        final Result<FeeFineAccountAndAction> processingFeeResult =
          getFeeFineOfType(feeFines, LOST_ITEM_PROCESSING_FEE_TYPE)
            .map(createAccountAndAction(context, policy.getItemProcessingFee()));

        accountsToCreate.add(processingFeeResult);
      }

      log.debug("Total accounts created {}", accountsToCreate.size());
      return combineAll(accountsToCreate);
    });
  }

  private UUID getOwnerServicePoint(Loan loan) {
    return loan.getItem().getLocation().getPrimaryServicePointId();
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private Function<FeeFine, FeeFineAccountAndAction> createAccountAndAction(
    ReferenceDataContext context, Optional<ItemFee> feeOptional) {

    return feeFine -> FeeFineAccountAndAction.builder()
      .withAmount(feeOptional.map(ItemFee::getAmount).orElse(ZERO))
      .withCreatedAt(getFeeFineActionCreatedAt(context))
      .withCreatedBy(context.staffUser)
      .withFeeFine(feeFine)
      .withFeeFineOwner(context.feeFineOwner)
      .withLoan(context.loan)
      .withItem(context.loan.getItem())
      .build();
  }

  private String getFeeFineActionCreatedAt(ReferenceDataContext context) {
    return context.servicePoint != null
      ? context.servicePoint.getName()
      : "";
  }

  private boolean shouldChargeItemFee(LostItemPolicy policy) {
    // Set cost fee is only supported now
    return policy.getSetCostFee()
      .filter(ItemFee::isChargeable)
      .isPresent();
  }

  private boolean shouldChargeProcessingFee(LostItemPolicy policy) {
    return policy.getItemProcessingFee().filter(ItemFee::isChargeable).isPresent();
  }

  private boolean shouldChargeAnyFee(LostItemPolicy policy) {
    return shouldChargeItemFee(policy) || shouldChargeProcessingFee(policy);
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
    private ServicePoint servicePoint;
    private User staffUser;
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

    public ReferenceDataContext withServicePoint(ServicePoint servicePoint) {
      this.servicePoint = servicePoint;
      return this;
    }

    public ReferenceDataContext withStaffUser(User staffUser) {
      this.staffUser = staffUser;
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
