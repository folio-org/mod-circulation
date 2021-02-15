package org.folio.circulation.services;

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

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountCancelReason;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LostItemFeeChargingService {
  private static final Logger log = LoggerFactory.getLogger(LostItemFeeChargingService.class);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;
  private final FeeFineRepository feeFineRepository;
  private final FeeFineFacade feeFineFacade;
  private final StoreLoanAndItem storeLoanAndItem;
  private final LocationRepository locationRepository;
  private final EventPublisher eventPublisher;
  private final FeeFineScheduledNoticeService feeFineScheduledNoticeService;
  private final LostItemFeeRefundService lostItemFeeRefundService;
  private final Clients clients;
  private final LostItemFeeRefundService refundService;
  private final AccountRepository accountRepository;
  private Loan loanWithAccountData;

  public LostItemFeeChargingService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.feeFineFacade = new FeeFineFacade(clients);
    this.storeLoanAndItem = new StoreLoanAndItem(clients);
    this.locationRepository = LocationRepository.using(clients);
    this.eventPublisher = new EventPublisher(clients.pubSubPublishingService());
    this.feeFineScheduledNoticeService = FeeFineScheduledNoticeService.using(clients);
    this.lostItemFeeRefundService = new LostItemFeeRefundService(clients);
    this.clients = clients;
    this.refundService = new LostItemFeeRefundService(clients);
    this.accountRepository = new AccountRepository(clients);

  }

  public CompletableFuture<Result<Loan>> chargeLostItemFees(
    Loan loan, DeclareItemLostRequest request, String staffUserId) {

    final ReferenceDataContext referenceDataContext = new ReferenceDataContext(
      loan, request, staffUserId);
    final AccountCancelReason reason = AccountCancelReason.CANCELLED_ITEM_DECLARED_LOST;
    final LostItemFeeRefundContext refundContext = new LostItemFeeRefundContext(
      loan.getItem().getStatus(),
      loan.getItem().getItemId(),
      staffUserId,
      request.getServicePointId(),
      loan,
      reason
    );

    return lostItemPolicyRepository.getLostItemPolicyById(loan.getLostItemPolicyId())
      .thenApply(result -> result.map(referenceDataContext::withLostItemPolicy))
      .thenCompose(refDataResult -> refDataResult.after(referenceData -> {
        if (shouldCloseLoan(referenceData.lostItemPolicy)) {
          log.debug("Loan [{}] can be closed because no fee will be charged", loan.getId());
          return closeLoanAndPublishEvent(loan);
        }
        try {
          loanWithAccountData = accountRepository.findAccountsForLoan(loan).join().value();
        } catch (Exception e) {
          log.error("Cannot retrieve account data for loan [{}], aborting charge fines", loan.getId());
          return closeLoanAndPublishEvent(loan);
        }

        if (hasLostItemFees(loanWithAccountData)) {
          log.info("Found pre-existing lost item fees for loan, attempting to remove", loan.getId());
          Result<LostItemFeeRefundContext> refund = refundService.refundAccounts(refundContext).join();
          if (refund.failed()) {
            log.error("Cannot refund and cancel existing fees for loan [{}]", loan.getId());
            return closeLoanAndPublishEvent(loan);
          }
        }

        return fetchFeeFineOwner(referenceData)
          .thenApply(this::refuseWhenFeeFineOwnerIsNotFound)
          .thenComposeAsync(this::fetchFeeFineTypes)
          .thenApply(this::buildAccountsAndActions)
          .thenCompose(r -> r.after(feeFineFacade::createAccounts))
          .thenApply(r -> r.map(notUsed -> loan));
      }));
    }

  private Boolean isOpenLostItemFee(Account account) {
    String type = account.getFeeFineType();

    log.info("account type is: " + account.getFeeFineType());
    log.info("account is open: " + account.isOpen());
    log.info("amount is: " + account.getAmount());
    if ((type == FeeFine.LOST_ITEM_FEE_TYPE || type == FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE) && account.isOpen()) {
      return true;
    } else {
      return false;
    }
  }

  private Boolean hasLostItemFees(Loan loan) {
    return loan.getAccounts().stream().anyMatch(account -> isOpenLostItemFee(account));
  }

  private CompletableFuture<Result<Loan>> closeLoanAndPublishEvent(Loan loan) {
    return closeLoanAndUpdateInStorage(loan)
            .thenCompose(r -> r.after(eventPublisher::publishClosedLoanEvent))
            .thenApply(r -> r.map(v -> loan));
  }

  private CompletableFuture<Result<Loan>> closeLoanAndUpdateInStorage(Loan loan) {
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
