package org.folio.circulation.services;

import static org.folio.circulation.domain.AccountCancelReason.CANCELLED_ITEM_DECLARED_LOST;
import static org.folio.circulation.domain.ActualCostFeeCancelReason.AGED_TO_LOST_ITEM_DECLARED_LOST;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.combineAll;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.With;

public class LostItemFeeChargingService {
  private static final Logger log = LogManager.getLogger(LostItemFeeChargingService.class);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineRepository feeFineRepository;
  private final FeeFineFacade feeFineFacade;
  private final StoreLoanAndItem storeLoanAndItem;
  private final LocationRepository locationRepository;
  private final EventPublisher eventPublisher;
  private final LostItemFeeRefundService refundService;
  private final ActualCostRecordService actualCostRecordService;

  public LostItemFeeChargingService(Clients clients,
    StoreLoanAndItem storeLoanAndItem, LostItemFeeRefundService refundService) {

    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.feeFineFacade = new FeeFineFacade(clients);
    this.storeLoanAndItem = storeLoanAndItem;
    this.locationRepository = LocationRepository.using(clients,
      new ServicePointRepository(clients));
    this.eventPublisher = new EventPublisher(clients.pubSubPublishingService());
    this.refundService = refundService;
    this.actualCostRecordService = new ActualCostRecordService(
      new ActualCostRecordRepository(clients), locationRepository,
      new IdentifierTypeRepository(clients), new PatronGroupRepository(clients));
  }

  public CompletableFuture<Result<Loan>> chargeLostItemFees(
    DeclareLostContext declareLostContext, String staffUserId) {

    Loan loan = declareLostContext.getLoan();
    final LostItemFeeRefundContext refundContext = new LostItemFeeRefundContext(
      loan.getItem().getStatus(), loan.getItem().getItemId(), staffUserId,
      declareLostContext.getRequest().getServicePointId(), loan,
      CANCELLED_ITEM_DECLARED_LOST, AGED_TO_LOST_ITEM_DECLARED_LOST);

    return refundService.refundLostItemFees(refundContext)
      .thenCompose(r -> r.after(refundCtx -> buildReferenceDataContext(refundCtx,
        declareLostContext)))
      .thenCompose(r -> r.after(this::applyLostItemFeePolicy));
  }

  private CompletableFuture<Result<ReferenceDataContext>> buildReferenceDataContext(
    LostItemFeeRefundContext refundContext, DeclareLostContext declareLostContext) {

    log.debug("buildReferenceDataContext:: context={}", refundContext);

    var loan = declareLostContext.getLoan();
    var lostItemPolicy = loan.getLostItemPolicy();

    ReferenceDataContext referenceDataContext = new ReferenceDataContext()
      .withLoan(loan)
      .withServicePointId(refundContext.getServicePointId())
      .withStaffUserId(refundContext.getStaffUserId())
      .withFeeFineOwner(declareLostContext.getFeeFineOwner());

    if (lostItemPolicy != null) {
      log.debug("buildReferenceDataContext:: skip fetching lost item fee policy {}",
        lostItemPolicy.getId());
      return ofAsync(referenceDataContext.withLostItemPolicy(lostItemPolicy));
    }

    return lostItemPolicyRepository.getLostItemPolicyById(loan.getLostItemPolicyId())
      .thenApply(r -> r.map(referenceDataContext::withLostItemPolicy));
  }

  private CompletableFuture<Result<Loan>> applyLostItemFeePolicy(ReferenceDataContext context) {
    log.debug("applyLostItemFeePolicy:: context={}", context);

    Loan loan = context.getLoan();
    if (shouldCloseLoan(context.getLostItemPolicy())) {
      log.info("applyLostItemFeePolicy:: closing loan {} as lost and paid", loan.getId());
      return closeLoanAsLostAndPaidAndPublishEvent(loan);
    } else {
      log.info("applyLostItemFeePolicy:: charging fees for loan {}", loan.getId());
      return applyFees(context, loan);
    }
  }

  private CompletableFuture<Result<Loan>> applyFees(ReferenceDataContext referenceData, Loan loan) {
    return fetchFeeFineTypes(succeeded(referenceData))
    .thenComposeAsync(r -> r.after(actualCostRecordService::createIfNecessaryForDeclaredLostItem))
    .thenApply(this::buildAccountsAndActions)
    .thenCompose(r -> r.after(feeFineFacade::createAccounts))
    .thenApply(r -> r.map(notUsed -> loan));
  }

  private CompletableFuture<Result<Loan>> closeLoanAsLostAndPaidAndPublishEvent(Loan loan) {
    return closeLoanAsLostAndPaidAndUpdateInStorage(loan)
    .thenCompose(r -> r.after(eventPublisher::publishClosedLoanEvent))
    .thenApply(r -> r.map(v -> loan));
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
      .withCurrentServicePointId(context.servicePointId)
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

  @Getter
  @AllArgsConstructor
  @NoArgsConstructor
  @With
  @ToString(onlyExplicitlyIncluded = true)
  public static final class ReferenceDataContext {
    @ToString.Include
    private Loan loan;
    @ToString.Include
    private String servicePointId;
    @ToString.Include
    private String staffUserId;
    private LostItemPolicy lostItemPolicy;
    private FeeFineOwner feeFineOwner;
    private Collection<FeeFine> feeFines;
    private ActualCostRecord actualCostRecord;
  }
}
