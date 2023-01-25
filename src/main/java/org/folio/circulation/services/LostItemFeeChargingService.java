package org.folio.circulation.services;

import static org.folio.circulation.domain.AccountCancelReason.CANCELLED_ITEM_DECLARED_LOST;
import static org.folio.circulation.domain.ActualCostFeeCancelReason.AGED_TO_LOST_ITEM_DECLARED_LOST;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.combineAll;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineRepository;
import org.folio.circulation.infrastructure.storage.inventory.IdentifierTypeRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.services.actualcostrecord.ActualCostRecordService;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import lombok.Getter;

public class LostItemFeeChargingService {
  private static final Logger log = LogManager.getLogger(LostItemFeeChargingService.class);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;
  private final FeeFineRepository feeFineRepository;
  private final FeeFineFacade feeFineFacade;
  private final StoreLoanAndItem storeLoanAndItem;
  private final LocationRepository locationRepository;
  private final EventPublisher eventPublisher;
  private final LostItemFeeRefundService refundService;
  private final AccountRepository accountRepository;
  private final ActualCostRecordService actualCostRecordService;
  private String userId;
  private String servicePointId;

  public LostItemFeeChargingService(Clients clients,
    StoreLoanAndItem storeLoanAndItem, LostItemFeeRefundService refundService) {

    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.feeFineFacade = new FeeFineFacade(clients);
    this.storeLoanAndItem = storeLoanAndItem;
    this.locationRepository = LocationRepository.using(clients,
      new ServicePointRepository(clients));
    this.eventPublisher = new EventPublisher(clients.pubSubPublishingService());
    this.refundService = refundService;
    this.accountRepository = new AccountRepository(clients);
    this.actualCostRecordService = new ActualCostRecordService(
      new ActualCostRecordRepository(clients), locationRepository,
      new IdentifierTypeRepository(clients), new PatronGroupRepository(clients));
  }

  public CompletableFuture<Result<Loan>> chargeLostItemFees(
    Loan loan, DeclareItemLostRequest request, String staffUserId) {
    this.userId = staffUserId;
    this.servicePointId = request.getServicePointId();

    final ReferenceDataContext referenceDataContext = new ReferenceDataContext(
      loan, request, staffUserId);

    return lostItemPolicyRepository.getLostItemPolicyById(loan.getLostItemPolicyId())
      .thenApply(result -> result.map(referenceDataContext::withLostItemPolicy))
      .thenCompose(refDataResult -> refDataResult.after(referenceData -> {
        log.info("Checking for existing lost item fees for loan [{}]", loan.getId());
        return accountRepository.findAccountsForLoan(loan)
          .thenCompose(result -> result.after(
            loanWithAccountData -> chargeLostItemFees(loanWithAccountData, referenceData)));
        }));
  }

  private CompletableFuture<Result<Loan>> chargeLostItemFees(Loan loan, ReferenceDataContext referenceData) {
    if (!hasLostItemFees(loan) && !shouldCloseLoan(referenceData.lostItemPolicy)) {
      log.info("No existing lost item fees found, applying lost item fees to loan [{}]", loan.getId());
      return applyFees(referenceData, loan);
    }

    if (!hasLostItemFees(loan) && shouldCloseLoan(referenceData.lostItemPolicy)) {
      log.info("No existing lost item fees found, closing loan [{}] as lost and paid.", loan.getId());
      return closeLoanAsLostAndPaidAndPublishEvent(loan);
    }
    log.info("Existing lost item fees found for loan [{}], trying to clear", loan.getId());
    return removeAndRefundFees(userId, servicePointId, loan)
      .thenCompose(refundResult -> refundResult.after(unused -> {
        log.info("Existing lost item fees cleared from loan [{}]", loan.getId());
        if (shouldCloseLoan(referenceData.lostItemPolicy)) {
          log.info("Closing loan [{}] as lost and paid.", loan.getId());
          return closeLoanAsLostAndPaidAndPublishEvent(loan);
        } else {
          log.info("Applying fees to loan [{}].", loan.getId());
          return applyFees(referenceData, loan);
        }
      }));
  }

  private CompletableFuture<Result<Loan>> applyFees(ReferenceDataContext referenceData, Loan loan) {
    return fetchFeeFineOwner(referenceData)
    .thenApply(this::refuseWhenFeeFineOwnerIsNotFound)
    .thenComposeAsync(this::fetchFeeFineTypes)
    .thenComposeAsync(r -> r.after(actualCostRecordService::createIfNecessaryForDeclaredLostItem))
    .thenApply(this::buildAccountsAndActions)
    .thenCompose(r -> r.after(feeFineFacade::createAccounts))
    .thenApply(r -> r.map(notUsed -> loan));
  }

  private CompletableFuture<Result<Loan>> removeAndRefundFees(String userId, String servicePointId,
    Loan loan) {

    final LostItemFeeRefundContext refundContext = new LostItemFeeRefundContext(
      loan.getItem().getStatus(), loan.getItem().getItemId(), userId, servicePointId, loan,
      CANCELLED_ITEM_DECLARED_LOST, AGED_TO_LOST_ITEM_DECLARED_LOST);

    return refundService.refundLostItemFees(refundContext)
      .thenCompose(refundResult -> {
        if (refundResult.failed()) {
          return CompletableFuture.completedFuture(failed(refundResult.cause()));
        } else {
          return CompletableFuture.completedFuture(succeeded(loan));
        }
      });
  }

  private CompletableFuture<Result<Loan>> closeLoanAsLostAndPaidAndPublishEvent(Loan loan) {
    return closeLoanAsLostAndPaidAndUpdateInStorage(loan)
    .thenCompose(r -> r.after(eventPublisher::publishClosedLoanEvent))
    .thenApply(r -> r.map(v -> loan));
  }

  private Boolean isOpenLostItemFee(Account account) {
    String type = account.getFeeFineType();
    return FeeFine.lostItemFeeTypes().contains(type) && account.isOpen();
  }

  private Boolean hasLostItemFees(Loan loan) {
    return loan.getAccounts().stream().anyMatch(account -> isOpenLostItemFee(account));
  }

  private CompletableFuture<Result<Loan>> closeLoanAsLostAndPaidAndUpdateInStorage(Loan loan) {
    loan.closeLoanAsLostAndPaid();
    return storeLoanAndItem.updateLoanAndItemInStorage(loan);
  }

  private boolean shouldCloseLoan(LostItemPolicy policy) {
    return !policy.getDeclareLostProcessingFee().isChargeable()
      && policy.hasNoLostItemFee();
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchFeeFineTypes(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.combineAfter(
      context -> feeFineRepository.getAutomaticFeeFines(lostItemFeeTypes()),
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

      if (policy.getDeclareLostProcessingFee().isChargeable()) {
        log.debug("Charging lost item processing fee");
        final Result<CreateAccountCommand> processingFeeResult =
          getFeeFineOfType(feeFines, LOST_ITEM_PROCESSING_FEE_TYPE)
            .map(createAccountCreation(context, policy.getDeclareLostProcessingFee()));

        accountsToCreate.add(processingFeeResult);
      }
      log.debug("Total accounts created {}", accountsToCreate.size());
      return combineAll(accountsToCreate);
    });
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
      .withLoanPolicyId(context.loan.getLoanPolicyId())
      .withOverdueFinePolicyId(context.loan.getOverdueFinePolicyId())
      .withLostItemFeePolicyId(context.loan.getLostItemPolicyId())
      .build();
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchFeeFineOwner(ReferenceDataContext referenceData) {
    final String permanentLocationId = referenceData.loan.getItem().getPermanentLocationId();

    return locationRepository.fetchLocationById(permanentLocationId)
      .thenApply(r -> r.map(Location::getPrimaryServicePointId))
      .thenCompose(r -> r.after(feeFineOwnerRepository::findOwnerForServicePoint))
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
      context -> singleValidationError("No fee/fine owner found for item's permanent location",
        "locationId", context.loan.getItem().getPermanentLocationId()));
  }

  @Getter
  public static final class ReferenceDataContext {
    private Loan loan;
    private final DeclareItemLostRequest request;
    private final String staffUserId;

    private LostItemPolicy lostItemPolicy;
    private FeeFineOwner feeFineOwner;
    private Collection<FeeFine> feeFines;
    private ActualCostRecord actualCostRecord;

    public ReferenceDataContext(Loan loan, DeclareItemLostRequest request, String staffUserId) {
      this.loan = loan;
      this.request = request;
      this.staffUserId = staffUserId;
    }

    public ReferenceDataContext withLostItemPolicy(LostItemPolicy lostItemPolicy) {
      this.lostItemPolicy = lostItemPolicy;
      this.loan = loan.withLostItemPolicy(lostItemPolicy);
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

    public ReferenceDataContext withActualCostRecord(ActualCostRecord actualCostRecord) {
      this.actualCostRecord = actualCostRecord;
      return this;
    }
  }
}
