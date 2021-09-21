package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.circulation.domain.OverdueFineCalculatorService.Scenario.CHECKIN;
import static org.folio.circulation.domain.OverdueFineCalculatorService.Scenario.RENEWAL;
import static org.folio.circulation.domain.representations.CheckInByBarcodeRequest.ClaimedReturnedResolution.FOUND_BY_LIBRARY;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.policy.OverdueFineCalculationParameters;
import org.folio.circulation.domain.policy.OverdueFineInterval;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.representations.StoredAccount;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.results.ResultBinding;
import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;

import lombok.AllArgsConstructor;
import lombok.With;

@AllArgsConstructor
public class OverdueFineCalculatorService {
  private final Repos repos;
  private final OverduePeriodCalculatorService overduePeriodCalculatorService;

  public static OverdueFineCalculatorService using(Clients clients) {
    return new OverdueFineCalculatorService(clients);
  }

  private OverdueFineCalculatorService(Clients clients) {
    this(
      new Repos(new OverdueFinePolicyRepository(clients),
        new AccountRepository(clients),
        new ItemRepository(clients, true, false, false),
        new FeeFineOwnerRepository(clients),
        new FeeFineRepository(clients),
        new UserRepository(clients),
        new FeeFineActionRepository(clients),
        new LostItemPolicyRepository(clients),
        ScheduledNoticesRepository.using(clients),
        new ServicePointRepository(clients)),
      new OverduePeriodCalculatorService(new CalendarRepository(clients),
        new LoanPolicyRepository(clients))
    );
  }

  public CompletableFuture<Result<RenewalContext>> createOverdueFineIfNecessary(
    RenewalContext context) {

    final String loggedInUserId = context.getLoggedInUserId();
    final Loan loanBeforeRenewal = context.getLoanBeforeRenewal();

    if (!shouldChargeOverdueFineOnRenewal(context)) {
      return completedFuture(succeeded(context));
    }

    return createOverdueFineIfNecessary(loanBeforeRenewal, RENEWAL, loggedInUserId)
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

    if (!shouldChargeOverdueFineOnCheckIn(context)) {
      return completedFuture(succeeded(null));
    }

    return createOverdueFineIfNecessary(context.getLoan(), CHECKIN, userId);
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
    Scenario scenario, String loggedInUserId) {

    return repos.overdueFinePolicyRepository.findOverdueFinePolicyForLoan(succeeded(loan))
    .thenCompose(r -> r.after(l -> scenario.shouldCreateFine(l.getOverdueFinePolicy())
        ? createOverdueFine(l, loggedInUserId)
        : completedFuture(succeeded(null))));
  }

  private CompletableFuture<Result<FeeFineAction>> createOverdueFine(Loan loan, String loggedInUserId) {
    return getOverdueMinutes(loan)
      .thenCompose(r -> r.after(minutes -> calculateOverdueFine(loan, minutes)))
      .thenCompose(r -> r.after(fine -> createFeeFineRecord(loan, fine, loggedInUserId)));
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan) {
    DateTime systemTime = loan.getReturnDate();
    if (systemTime == null) {
      systemTime = ClockUtil.getDateTime();
    }
    return overduePeriodCalculatorService.getMinutes(loan, systemTime);
  }

  private CompletableFuture<Result<BigDecimal>> calculateOverdueFine(Loan loan, Integer overdueMinutes) {
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

    return CompletableFuture.completedFuture(succeeded(overdueFine));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupItemRelatedRecords(
    CalculationParameters params) {

    if (params.feeFine == null) {
      return completedFuture(succeeded(params));
    }

    return repos.itemRepository.fetchItemRelatedRecords(succeeded(params.loan.getItem()))
      .thenApply(mapResult(params::withItem));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFineOwner(
    CalculationParameters params) {

    return Optional.ofNullable(params.item)
      .map(Item::getLocation)
      .map(Location::getPrimaryServicePointId)
      .map(UUID::toString)
      .map(id -> repos.feeFineOwnerRepository.findOwnerForServicePoint(id)
        .thenApply(mapResult(params::withFeeFineOwner)))
      .orElse(completedFuture(succeeded(params)));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFine(
    CalculationParameters params) {

    return repos.feeFineRepository.getFeeFine(FeeFine.OVERDUE_FINE_TYPE, true)
      .thenApply(mapResult(params::withFeeFine));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupLoggedInUser(
    CalculationParameters params, String loggedInUserId) {

    return repos.userRepository.getUser(loggedInUserId)
      .thenApply(ResultBinding.mapResult(params::withLoggedInUser));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupLoanServicePoints(
    CalculationParameters params) {

    return repos.servicePointRepository.findServicePointsForLoan(succeeded(params.loan))
      .thenApply(mapResult(params::withLoan));
  }

  private CompletableFuture<Result<FeeFineAction>> createFeeFineRecord(Loan loan, BigDecimal fineAmount,
    String loggedInUserId) {

    if (fineAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return completedFuture(succeeded(null));
    }

    return completedFuture(succeeded(
      new CalculationParameters(loan)))
      .thenCompose(r -> r.after(this::lookupFeeFine))
      .thenCompose(r -> r.after(this::lookupItemRelatedRecords))
      .thenCompose(r -> r.after(this::lookupFeeFineOwner))
      .thenCompose(r -> r.after(params -> this.lookupLoggedInUser(params, loggedInUserId)))
      .thenCompose(r -> r.after(this::lookupLoanServicePoints))
      .thenCompose(r -> r.after(params -> createAccount(fineAmount, params)))
      .thenCompose(r -> r.after(feeFineAction -> repos.scheduledNoticesRepository
          .deleteOverdueNotices(loan.getId())
          .thenApply(rs -> r)));
  }

  private CompletableFuture<Result<FeeFineAction>> createAccount(BigDecimal fineAmount,
    CalculationParameters params) {

    if (params.isIncomplete()) {
      return completedFuture(succeeded(null));
    }

    StoredAccount accountRepresentation =
      new StoredAccount(params.loan, params.item, params.feeFineOwner,
        params.feeFine, new FeeAmount(fineAmount));

    return repos.accountRepository.create(accountRepresentation)
      .thenCompose(rac -> rac.after(account -> createFeeFineAction(account, params)));
  }

  private CompletableFuture<Result<FeeFineAction>> createFeeFineAction(
    Account account, CalculationParameters params) {

    String checkInServicePoint = Optional.ofNullable(params.loan)
      .map(Loan::getCheckinServicePoint)
      .map(ServicePoint::getName)
      .orElse(StringUtils.EMPTY);

    return repos.feeFineActionRepository.create(StoredFeeFineAction.builder()
      .useAccount(account)
      .withAction(account.getFeeFineType())
      .withCreatedAt(checkInServicePoint)
      .withCreatedBy(params.loggedInUser)
      .build());
  }

  private boolean itemWasLost(ItemStatus itemStatus) {
    return itemStatus != null && itemStatus.isLostNotResolved();
  }

  @With
  @AllArgsConstructor(access = PRIVATE)
  private static class CalculationParameters {
    final Loan loan;
    final Item item;
    final FeeFineOwner feeFineOwner;
    final FeeFine feeFine;
    final User loggedInUser;

    CalculationParameters(Loan loan) {
      this(loan, null, null, null, null);
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

  @AllArgsConstructor
  public static class Repos {
    private final OverdueFinePolicyRepository overdueFinePolicyRepository;
    private final AccountRepository accountRepository;
    private final ItemRepository itemRepository;
    private final FeeFineOwnerRepository feeFineOwnerRepository;
    private final FeeFineRepository feeFineRepository;
    private final UserRepository userRepository;
    private final FeeFineActionRepository feeFineActionRepository;
    private final LostItemPolicyRepository lostItemPolicyRepository;
    private final ScheduledNoticesRepository scheduledNoticesRepository;
    private final ServicePointRepository servicePointRepository;
  }
}
