package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.LoanToChargeOverdueFine.forCheckIn;
import static org.folio.circulation.domain.LoanToChargeOverdueFine.forRenewal;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.CommonUtils.pair;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.tuple.Pair;
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

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

// Constructor required for tests
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class OverdueFineCalculatorService {
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
  private final OverduePeriodCalculatorService overduePeriodCalculatorService;

  public OverdueFineCalculatorService(Clients clients) {
    overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    accountRepository = new AccountRepository(clients);
    itemRepository = new ItemRepository(clients, true, false, false);
    feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    feeFineRepository = new FeeFineRepository(clients);
    userRepository = new UserRepository(clients);
    feeFineActionRepository = new FeeFineActionRepository(clients);
    lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    scheduledNoticesRepository = ScheduledNoticesRepository.using(clients);
    servicePointRepository = new ServicePointRepository(clients);
    overduePeriodCalculatorService = new OverduePeriodCalculatorService(
      new CalendarRepository(clients), new LoanPolicyRepository(clients));
  }

  public CompletableFuture<Result<RenewalContext>> createOverdueFineIfNecessary(RenewalContext context) {
    return chargeOverdueFine(forRenewal(context))
      .thenApply(mapResult(context::withOverdueFeeFineAction));
  }

  public CompletableFuture<Result<FeeFineAction>> createOverdueFineIfNecessary(CheckInContext context) {
    return chargeOverdueFine(forCheckIn(context));
  }

  private CompletableFuture<Result<FeeFineAction>> chargeOverdueFine(
    LoanToChargeOverdueFine loanToCharge) {

    if (loanToCharge.loanIsUndefined() || !loanToCharge.isOverdue()) {
      return ofAsync(() -> null);
    }

    return fetchRequiredPolicies(loanToCharge)
      .thenCompose(r -> r.after(loanToChargeWithPolicies -> {
        if (!loanToChargeWithPolicies.shouldChargeOverdueFine()) {
          return completedFuture(succeeded(null));
        }

        return createOverdueFine(loanToChargeWithPolicies);
      }));
  }

  private CompletableFuture<Result<LoanToChargeOverdueFine>> fetchRequiredPolicies(
    LoanToChargeOverdueFine loanToCharge) {

    return overdueFinePolicyRepository
      .findOverdueFinePolicyForLoan(succeeded(loanToCharge.getLoan()))
      .thenApply(r -> r.map(loanToCharge::withLoan))
      .thenComposeAsync(r -> r.after(loanToChargeWithPolicy -> {
        if (!loanToChargeWithPolicy.wasDeclaredLost()) {
          return ofAsync(() -> loanToChargeWithPolicy);
        }

        return lostItemPolicyRepository
          .findLostItemPolicyForLoan(succeeded(loanToChargeWithPolicy.getLoan()))
          .thenApply(policyResult -> policyResult.map(loanToChargeWithPolicy::withLoan));
      }));
  }

  private CompletableFuture<Result<FeeFineAction>> createOverdueFine(
    LoanToChargeOverdueFine loanToCharge) {

    return overduePeriodCalculatorService.getMinutes(loanToCharge)
      .thenCompose(r -> r.after(minutes -> calculateOverdueFine(loanToCharge, minutes)))
      .thenCompose(r -> r.after(fine -> createFeeFineRecord(loanToCharge, fine)));
  }

  private CompletableFuture<Result<Double>> calculateOverdueFine(
    LoanToChargeOverdueFine loan, Integer overdueMinutes) {

    double overdueFine = 0.0;

    OverdueFinePolicy overdueFinePolicy = loan.getOverdueFinePolicy();
    if (overdueMinutes > 0 && overdueFinePolicy != null) {
      OverdueFineCalculationParameters calculationParameters =
        overdueFinePolicy.getCalculationParameters(loan.wasDueDateChangedByRecall());

      if (calculationParameters != null) {
        Double finePerInterval = calculationParameters.getFinePerInterval();
        OverdueFineInterval interval = calculationParameters.getInterval();
        Double maxFine = calculationParameters.getMaxFine();

        if (maxFine != null && interval != null && finePerInterval != null) {
          double numberOfIntervals = Math.ceil(overdueMinutes.doubleValue() /
            interval.getMinutes().doubleValue());

          overdueFine = finePerInterval * numberOfIntervals;

          if (maxFine > 0) {
            overdueFine = Math.min(overdueFine, maxFine);
          }
        }
      }
    }

    return CompletableFuture.completedFuture(succeeded(overdueFine));
  }

  private CompletableFuture<Result<LoanToChargeOverdueFine>> lookupItemRelatedRecords(
    LoanToChargeOverdueFine loanToCharge) {

    if (loanToCharge.getFeeFine() == null) {
      return completedFuture(succeeded(loanToCharge));
    }

    return itemRepository.fetchItemRelatedRecords(succeeded(loanToCharge.getLoan().getItem()))
      .thenApply(mapResult(loanToCharge::withItem));
  }

  private CompletableFuture<Result<LoanToChargeOverdueFine>> lookupFeeFineOwner(
    LoanToChargeOverdueFine loan) {

    return Optional.ofNullable(loan.getItem())
      .map(Item::getLocation)
      .map(Location::getPrimaryServicePointId)
      .map(UUID::toString)
      .map(id -> feeFineOwnerRepository.findOwnerForServicePoint(id)
        .thenApply(mapResult(loan::withFeeFineOwner)))
      .orElse(completedFuture(succeeded(loan)));
  }

  private CompletableFuture<Result<LoanToChargeOverdueFine>> lookupFeeFine(
    LoanToChargeOverdueFine loan) {

    return feeFineRepository.getFeeFine(FeeFine.OVERDUE_FINE_TYPE, true)
      .thenApply(mapResult(loan::withFeeFine));
  }

  private CompletableFuture<Result<LoanToChargeOverdueFine>> lookupLoggedInUser(
    LoanToChargeOverdueFine loan) {

    return userRepository.getUser(loan.getLoggedInUserId())
      .thenApply(ResultBinding.mapResult(loan::withLoggedInUser));
  }

  private CompletableFuture<Result<LoanToChargeOverdueFine>> lookupLoanServicePoints(
    LoanToChargeOverdueFine loan) {

    return servicePointRepository.findServicePointsForLoan(succeeded(loan.getLoan()))
      .thenApply(mapResult(loan::withLoan));
  }

  private CompletableFuture<Result<FeeFineAction>> createFeeFineRecord(
    LoanToChargeOverdueFine loanToChargeOverdueFine, Double fineAmount) {

    if (fineAmount <= 0) {
      return completedFuture(succeeded(null));
    }

    return lookupFeeFine(loanToChargeOverdueFine)
      .thenCompose(r -> r.after(this::lookupItemRelatedRecords))
      .thenCompose(r -> r.after(this::lookupFeeFineOwner))
      .thenCompose(r -> r.after(this::lookupLoggedInUser))
      .thenCompose(r -> r.after(this::lookupLoanServicePoints))
      .thenCompose(r -> r.after(loanToCharge -> createAccount(fineAmount, loanToCharge)))
      .thenCompose(r -> r.after(feeFineAction -> scheduledNoticesRepository
        .deleteOverdueNotices(loanToChargeOverdueFine.getLoan().getId())
        .thenApply(rs -> r)));
  }

  private CompletableFuture<Result<FeeFineAction>> createAccount(Double fineAmount,
    LoanToChargeOverdueFine loan) {

    if (!loan.canCreateOverdueFine()) {
      return completedFuture(succeeded(null));
    }

    final StoredAccount accountRepresentation = new StoredAccount(
      loan.getLoan(), loan.getItem(), loan.getFeeFineOwner(), loan.getFeeFine(),
      new FeeAmount(fineAmount));

    return accountRepository.create(accountRepresentation)
      .thenCompose(rac -> rac.after(account -> createFeeFineAction(account, loan)));
  }

  private CompletableFuture<Result<FeeFineAction>> createFeeFineAction(
    Account account, LoanToChargeOverdueFine loan) {

    return feeFineActionRepository.create(StoredFeeFineAction.builder()
      .useAccount(account)
      .withAction(account.getFeeFineType())
      .withCreatedAt(loan.getLoan().getCheckinServicePoint())
      .withCreatedBy(loan.getLoggedInUser())
      .build());
  }
}
