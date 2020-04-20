package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.support.Result.combineAll;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import org.folio.circulation.domain.policy.lostitem.ChargeAmountType;
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
        if (!shouldChargeAnyFee(referenceData.lostItemPolicy)) {
          log.debug("No fee is going to be charged, skipping logic, loan id {}", loan.getId());
          return completedFuture(succeeded(loan));
        }

        return fetchFeeFineOwner(referenceData)
          .thenComposeAsync(this::fetchFeeFineTypes)
          .thenComposeAsync(this::fetchServicePoint)
          .thenComposeAsync(this::fetchStaffUser)
          .thenApply(this::buildAccountsAndActions)
          .thenCompose(r -> r.after(feeFineService::createAccountsAndActions))
          .thenApply(r -> r.map(notUsed -> loan));
      }));
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
      final FeeFineOwner owner = context.feeFineOwner;
      final Loan loan = context.loan;
      final LostItemPolicy policy = context.lostItemPolicy;
      final List<Result<FeeFineAccountAndAction>> accountsToCreate = new ArrayList<>();
      final Collection<FeeFine> feeFines = context.feeFines;

      if (owner == null) {
        log.warn("Cannot find owner for service point [{}], no fee will be charged for loan [{}]",
          getOwnerServicePoint(loan), loan.getId());

        return succeeded(Collections.emptyList());
      }

      if (shouldChargeItemFee(policy)) {
        log.debug("Charging lost item fee");

        final Result<FeeFineAccountAndAction> lostItemFeeResult =
          getFeeFineOfType(feeFines, LOST_ITEM_FEE_TYPE)
            .map(createAccountAndAction(context, policy.getChargeAmountItem().getAmount()));

        accountsToCreate.add(lostItemFeeResult);
      }

      if (shouldChargeProcessingFee(policy)) {
        log.debug("Charging lost item processing fee");

        final Result<FeeFineAccountAndAction> processingFeeResult =
          getFeeFineOfType(feeFines, LOST_ITEM_PROCESSING_FEE_TYPE)
            .map(createAccountAndAction(context, policy.getLostItemProcessingFee()));

        accountsToCreate.add(processingFeeResult);
      }

      log.debug("Total accounts created {}", accountsToCreate.size());
      return combineAll(accountsToCreate);
    });
  }

  private UUID getOwnerServicePoint(Loan loan) {
    return loan.getItem().getLocation().getPrimaryServicePointId();
  }

  private Function<FeeFine, FeeFineAccountAndAction> createAccountAndAction(
    ReferenceDataContext context, BigDecimal amount) {

    return feeFine -> FeeFineAccountAndAction.builder()
      .withAmount(amount)
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
    return policy.getChargeAmountItem().getChargeType() == ChargeAmountType.SET_COST
      && isGreaterThanZero(policy.getChargeAmountItem().getAmount());
  }

  private boolean shouldChargeProcessingFee(LostItemPolicy policy) {
    return policy.shouldChargeProcessingFee()
      && isGreaterThanZero(policy.getLostItemProcessingFee());
  }

  private boolean shouldChargeAnyFee(LostItemPolicy policy) {
    return shouldChargeItemFee(policy) || shouldChargeProcessingFee(policy);
  }

  private boolean isGreaterThanZero(BigDecimal numberToCompare) {
    return numberToCompare != null && numberToCompare.compareTo(BigDecimal.ZERO) > 0;
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
