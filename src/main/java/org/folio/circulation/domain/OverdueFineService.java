package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.circulation.domain.OverdueFineService.Scenario.CHECKIN;
import static org.folio.circulation.domain.OverdueFineService.Scenario.RENEWAL;
import static org.folio.circulation.domain.representations.CheckInByBarcodeRequest.ClaimedReturnedResolution.FOUND_BY_LIBRARY;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.policy.OverdueFineCalculationParameters;
import org.folio.circulation.domain.policy.OverdueFineInterval;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.FeeFineFacade;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.With;

@AllArgsConstructor
public class OverdueFineService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final OverdueFinePolicyRepository overdueFinePolicyRepository;
  private final ItemRepository itemRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;
  private final FeeFineRepository feeFineRepository;
  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final OverduePeriodCalculatorService overduePeriodCalculatorService;
  private final FeeFineFacade feeFineFacade;

  public CompletableFuture<Result<RenewalContext>> createOverdueFineIfNecessary(
    RenewalContext context) {

    log.debug("createOverdueFineIfNecessary:: parameters context: {}", () -> context);
    final String loggedInUserId = context.getLoggedInUserId();
    final Loan loanBeforeRenewal = context.getLoanBeforeRenewal();

    if (!shouldChargeOverdueFineOnRenewal(context)) {
      log.info("createOverdueFineIfNecessary:: loan on renewal should not be charged");
      return completedFuture(succeeded(context));
    }

    return createOverdueFineIfNecessary(loanBeforeRenewal, RENEWAL, loggedInUserId, context.getTimeZone())
      .thenApply(mapResult(context::withOverdueFeeFineAction));
  }

  private boolean shouldChargeOverdueFineOnRenewal(RenewalContext renewalContext) {
    final Loan loanBeforeRenewal = renewalContext.getLoanBeforeRenewal();
    if (loanBeforeRenewal == null || !loanBeforeRenewal.isOverdue()) {
      return false;
    }

    if (itemWasLost(renewalContext.getItemStatusBeforeRenewal())) {
      return shouldChargeOverdueFineForLostItem(renewalContext.getLoan());
    }

    return true;
  }

  public CompletableFuture<Result<FeeFineAction>> createOverdueFineIfNecessary(
    CheckInContext context, String userId) {

    log.debug("createOverdueFineIfNecessary:: parameters context: {}, userId: {}",
      () -> context, () -> userId);
    if (!shouldChargeOverdueFineOnCheckIn(context)) {
      log.info("createOverdueFineIfNecessary:: loan on check in should not be charged");
      return completedFuture(succeeded(null));
    }

    return createOverdueFineIfNecessary(context.getLoan(), CHECKIN, userId, context.getTimeZone());
  }

  private boolean shouldChargeOverdueFineOnCheckIn(CheckInContext context) {
    final Loan loan = context.getLoan();

    if (loan == null || !loan.isOverdue(loan.getReturnDate())) {
      return false;
    }

    if(context.getCheckInRequest().getClaimedReturnedResolution() != null
        && context.getCheckInRequest().getClaimedReturnedResolution().equals(FOUND_BY_LIBRARY)) {
      return false;
    }

    if (itemWasLost(context.getItemStatusBeforeCheckIn())) {
      return shouldChargeOverdueFineForLostItem(loan);
    }

    return true;
  }

  private boolean shouldChargeOverdueFineForLostItem(Loan loan) {
    if (!loan.getLostItemPolicy().shouldChargeOverdueFee()) {
      return false;
    }

    if (!loan.getLostItemPolicy().shouldRefundFees(loan.getLostDate())) {
      // if the refund period has passed, do not charge fines.
      return false;
    }

    return true;
  }

  private CompletableFuture<Result<FeeFineAction>> createOverdueFineIfNecessary(Loan loan,
    Scenario scenario, String loggedInUserId, ZoneId zoneId) {

    log.debug("createOverdueFineIfNecessary:: parameters loan: {}, scenario: {}, " +
      "loggedInUserId: {}", () -> loan, () -> scenario, () -> loggedInUserId);
    if (loan.getOverdueFinePolicy().isUnknown()) {
      return overdueFinePolicyRepository.findOverdueFinePolicyForLoan(succeeded(loan))
        .thenCompose(r -> r.after(l -> createOverdueFineIfNecessary(l, scenario, loggedInUserId,
          l.getOverdueFinePolicy(), zoneId)));
    } else {
      return createOverdueFineIfNecessary(loan, scenario, loggedInUserId, loan.getOverdueFinePolicy(), zoneId);
    }
  }

  private CompletableFuture<Result<FeeFineAction>> createOverdueFineIfNecessary(Loan loan,
    Scenario scenario, String loggedInUserId, OverdueFinePolicy overdueFinePolicy, ZoneId zoneId) {

    log.debug("createOverdueFineIfNecessary:: parameters loan: {}, scenario: {}, " +
      "loggedInUserId: {}, overdueFinePolicy: {}", () -> loan, () -> scenario, () -> loggedInUserId,
      () -> overdueFinePolicy);

    return scenario.shouldCreateFine(overdueFinePolicy)
      ? createOverdueFine(loan, loggedInUserId, zoneId)
      : completedFuture(succeeded(null));
  }

  private CompletableFuture<Result<FeeFineAction>> createOverdueFine(Loan loan, String loggedInUserId, ZoneId zoneId) {
    log.debug("createOverdueFine:: parameters loan: {}, loggedInUserId: {}",
      () -> loan, () -> loggedInUserId);

    return getOverdueMinutes(loan, zoneId)
      .thenCompose(r -> r.after(minutes -> calculateOverdueFine(loan, minutes)))
      .thenCompose(r -> r.after(fineAmount -> createFeeFineRecord(loan, fineAmount, loggedInUserId)));
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, ZoneId zoneId) {
    ZonedDateTime systemTime = loan.getReturnDate();
    if (systemTime == null) {
      log.info("getOverdueMinutes:: returnDate for loan {} is null", loan.getId());
      systemTime = ClockUtil.getZonedDateTime();
    }
    return overduePeriodCalculatorService.getMinutes(loan, systemTime, zoneId);
  }

  private CompletableFuture<Result<BigDecimal>> calculateOverdueFine(Loan loan, Integer overdueMinutes) {
    log.debug("calculateOverdueFine:: parameters loan: {}, overdueMinutes: {}",
      () -> loan ,() -> overdueMinutes);
    BigDecimal overdueFine = BigDecimal.ZERO;

    OverdueFinePolicy overdueFinePolicy = loan.getOverdueFinePolicy();
    if (overdueMinutes > 0 && overdueFinePolicy != null) {
      OverdueFineCalculationParameters calculationParameters =
        overdueFinePolicy.getCalculationParameters(loan.wasDueDateChangedByRecall());

      if (calculationParameters != null) {
        BigDecimal finePerInterval = calculationParameters.getFinePerInterval();
        OverdueFineInterval interval = calculationParameters.getInterval();
        BigDecimal maxFine = calculationParameters.getMaxFine();

        if (maxFine != null && interval != null && finePerInterval != null) {
          double numberOfIntervals = Math.ceil(overdueMinutes.doubleValue() /
            interval.getMinutes().doubleValue());

          overdueFine = finePerInterval.multiply(BigDecimal.valueOf(numberOfIntervals));

          if (maxFine.compareTo(BigDecimal.ZERO) > 0) {
            overdueFine = overdueFine.min(maxFine);
          }
        }
      }
    }
    log.info("calculateOverdueFine:: result: {}", overdueFine);

    return completedFuture(succeeded(overdueFine));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupItemRelatedRecords(
    CalculationParameters params) {

    log.debug("lookupItemRelatedRecords:: parameters params: {}", () -> params);
    if (params.feeFine == null) {
      return completedFuture(succeeded(params));
    }

    return itemRepository.fetchItemRelatedRecords(succeeded(params.loan.getItem()))
      .thenApply(mapResult(params::withItem));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFineOwner(
    CalculationParameters params) {

    log.debug("lookupFeeFineOwner:: parameters params: {}", () -> params);

    return Optional.ofNullable(params.item)
      .map(Item::getLocation)
      .map(Location::getPrimaryServicePointId)
      .map(UUID::toString)
      .map(id -> feeFineOwnerRepository.findOwnerForServicePoint(id)
        .thenApply(mapResult(params::withFeeFineOwner)))
      .orElse(completedFuture(succeeded(params)));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFine(
    CalculationParameters params) {

    log.debug("lookupFeeFine:: parameters params: {}", () -> params);

    return feeFineRepository.getFeeFine(FeeFine.OVERDUE_FINE_TYPE, true)
      .thenApply(mapResult(params::withFeeFine));
  }

  private CompletableFuture<Result<FeeFineAction>> createFeeFineRecord(Loan loan,
    BigDecimal fineAmount, String loggedInUserId) {

    log.debug("createFeeFineRecord:: parameters loan: {}, fineAmount: {}, loggedInUserId: {}",
      () -> loan, () -> fineAmount, () -> loggedInUserId);

    if (fineAmount.compareTo(BigDecimal.ZERO) <= 0) {
      log.info("createFeeFineRecord:: fineAmount is 0");
      return completedFuture(succeeded(null));
    }

    return ofAsync(() -> new CalculationParameters(loan, fineAmount, loggedInUserId))
      .thenCompose(r -> r.after(this::lookupFeeFine))
      .thenCompose(r -> r.after(this::lookupItemRelatedRecords))
      .thenCompose(r -> r.after(this::lookupFeeFineOwner))
      .thenCompose(r -> r.after(this::createAccount))
      .thenCompose(r -> r.after(feeFineAction -> scheduledNoticesRepository
          .deleteOverdueNotices(loan.getId())
          .thenApply(rs -> r)));
  }

  private CompletableFuture<Result<FeeFineAction>> createAccount(CalculationParameters params) {
    log.debug("createAccount:: parameters params: {}", () -> params);
    if (params.isIncomplete()) {
      log.info("createAccount:: params are incomplete");
      return completedFuture(succeeded(null));
    }

    var createAccountCommand = CreateAccountCommand.builder()
      .withLoan(params.loan)
      .withItem(params.item)
      .withFeeFineOwner(params.feeFineOwner)
      .withFeeFine(params.feeFine)
      .withAmount(new FeeAmount(params.fineAmount))
      .withStaffUserId(params.loggedInUserId)
      .withCurrentServicePointId(params.loan.getCheckInServicePointId())
      .withLoanPolicyId(params.loan.getLoanPolicyId())
      .withOverdueFinePolicyId(params.loan.getOverdueFinePolicyId())
      .withLostItemFeePolicyId(params.loan.getLostItemPolicyId())
      .build();

    return feeFineFacade.createAccount(createAccountCommand);
  }

  private boolean itemWasLost(ItemStatus itemStatus) {
    return itemStatus != null && itemStatus.isLostNotResolved();
  }

  @With
  @AllArgsConstructor(access = PRIVATE)
  @ToString(onlyExplicitlyIncluded = true)
  private static class CalculationParameters {
    @ToString.Include
    private final Loan loan;
    private final Item item;
    private final FeeFineOwner feeFineOwner;
    @ToString.Include
    private final FeeFine feeFine;
    @ToString.Include
    private final BigDecimal fineAmount;
    private final String loggedInUserId;

    CalculationParameters(Loan loan, BigDecimal fineAmount, String loggedInUserId) {
      this(loan, null, null, null, fineAmount, loggedInUserId);
    }

    boolean isComplete() {
      return ObjectUtils.allNotNull(loan, item, feeFineOwner, feeFine);
    }

    boolean isIncomplete() {
      return !isComplete();
    }
  }

  enum Scenario {
    CHECKIN(policy -> !policy.isUnknown()),
    RENEWAL(policy -> !policy.isUnknown() && isFalse(policy.getForgiveFineForRenewals()));

    private final Predicate<OverdueFinePolicy> shouldCreateFine;

    Scenario(Predicate<OverdueFinePolicy> shouldCreateFine) {
      this.shouldCreateFine = shouldCreateFine;
    }

    private boolean shouldCreateFine(OverdueFinePolicy overdueFinePolicy) {
      return shouldCreateFine.test(overdueFinePolicy);
    }
  }
}
