package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.folio.circulation.domain.policy.OverdueFineInterval;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.domain.representations.AccountStorageRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class OverdueFineCalculatorService {
  public static OverdueFineCalculatorService using(Clients clients) {
    return new OverdueFineCalculatorService(clients);
  }

  private OverdueFinePolicyRepository overdueFinePolicyRepository;
  private AccountRepository accountRepository;
  private ItemRepository itemRepository;
  private FeeFineOwnerRepository feeFineOwnerRepository;
  private FeeFineRepository feeFineRepository;
  private OverduePeriodCalculatorService overduePeriodCalculatorService;

  public OverdueFineCalculatorService(
    OverdueFinePolicyRepository overdueFinePolicyRepository,
    AccountRepository accountRepository,
    ItemRepository itemRepository,
    FeeFineOwnerRepository feeFineOwnerRepository,
    FeeFineRepository feeFineRepository,
    OverduePeriodCalculatorService overduePeriodCalculatorService) {
    this.overdueFinePolicyRepository = overdueFinePolicyRepository;
    this.accountRepository = accountRepository;
    this.itemRepository = itemRepository;
    this.feeFineOwnerRepository = feeFineOwnerRepository;
    this.feeFineRepository = feeFineRepository;
    this.overduePeriodCalculatorService = overduePeriodCalculatorService;
  }

  private OverdueFineCalculatorService(Clients clients) {
    this(new OverdueFinePolicyRepository(clients),
      new AccountRepository(clients),
      new ItemRepository(clients, true, false, false),
      new FeeFineOwnerRepository(clients),
      new FeeFineRepository(clients),
      new OverduePeriodCalculatorService(new CalendarRepository(clients)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> calculateForRenewal(
    LoanAndRelatedRecords records) {

    return createOverdueFineIfNecessary(records, Scenario.RENEWAL);
  }

  public CompletableFuture<Result<CheckInProcessRecords>> calculateForCheckIn(
    CheckInProcessRecords records) {

    return createOverdueFineIfNecessary(records, Scenario.CHECKIN);
  }

  <T extends LoanRelatedRecord> CompletableFuture<Result<T>> createOverdueFineIfNecessary(
    T records, Scenario scenario) {

    final Loan loan = records.getLoan();

    if (loan == null || !loan.isOverdue()) {
      return completedFuture(succeeded(records));
    }

    return completedFuture(succeeded(loan))
      .thenCompose(overdueFinePolicyRepository::findOverdueFinePolicyForLoan)
      .thenCompose(r -> r.after(l -> createOverdueFineIfNecessary(l, scenario)))
      .thenApply(mapResult(r -> records));
  }

  private CompletableFuture<Result<Void>> createOverdueFineIfNecessary(Loan loan, Scenario scenario) {
    if (scenario.shouldNotCreateFine.test(loan.getOverdueFinePolicy())) {
      return completedFuture(succeeded(null));
    }

    return getOverdueMinutes(loan)
      .thenCompose(r -> r.after(minutes -> calculateOverdueFine(loan, minutes)))
      .thenCompose(r -> r.after(fine -> createFeeFineRecord(loan, fine)));
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan) {
    DateTime systemTime = loan.getReturnDate();
    if (systemTime == null) {
      systemTime = DateTime.now(DateTimeZone.UTC);
    }
    return overduePeriodCalculatorService.getMinutes(loan, systemTime);
  }

  private CompletableFuture<Result<Double>> calculateOverdueFine(Loan loan, Integer overdueMinutes) {
    double overdueFine = 0.0;

    OverdueFinePolicy overdueFinePolicy = loan.getOverdueFinePolicy();
    if (overdueMinutes > 0 && overdueFinePolicy != null) {
      Double maxFine = loan.wasDueDateChangedByRecall() ?
        overdueFinePolicy.getMaxOverdueRecallFine() :
        overdueFinePolicy.getMaxOverdueFine();

      OverdueFineInterval interval = overdueFinePolicy.getOverdueFineInterval();
      Double overdueFinePerInterval = loan.getOverdueFinePolicy().getOverdueFine();

      if (maxFine != null && interval != null && overdueFinePerInterval != null) {
        double numberOfIntervals = Math.ceil(overdueMinutes.doubleValue() /
          interval.getMinutes().doubleValue());
        overdueFine = overdueFinePerInterval * numberOfIntervals;

        if (maxFine > 0) {
          overdueFine = Math.min(overdueFine, maxFine);
        }
      }
    }

    return CompletableFuture.completedFuture(succeeded(overdueFine));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupItemRelatedRecords(
    CalculationParameters params) {
    return itemRepository.fetchItemRelatedRecords(succeeded(params.loan.getItem()))
      .thenApply(mapResult(params::withItem));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFineOwner(
    CalculationParameters params) {
    return feeFineOwnerRepository.getFeeFineOwner(
      params.item.getLocation().getPrimaryServicePointId().toString())
      .thenApply(mapResult(params::withOwner));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFine(
    CalculationParameters params) {
    if (params.feeFineOwner == null) {
      return completedFuture(succeeded(params));
    }
    return feeFineRepository.getOverdueFine(params.feeFineOwner.getId())
      .thenApply(mapResult(params::withFeeFine))
      .thenCompose(r -> r.after(updatedParams -> {
        if (updatedParams.feeFine == null) {
          FeeFine feeFine = new FeeFine(UUID.randomUUID().toString(),
            updatedParams.feeFineOwner.getId(), FeeFine.OVERDUE_FINE_TYPE);
          return feeFineRepository.create(feeFine)
            .thenApply(mapResult(params::withFeeFine));
        }
        return completedFuture(succeeded(updatedParams));
      }));
  }

  private CompletableFuture<Result<Void>> createFeeFineRecord(Loan loan, Double fineAmount) {
    if (fineAmount <= 0) {
      return completedFuture(succeeded(null));
    }

    return CompletableFuture.completedFuture(succeeded(
      new CalculationParameters(loan, null, null, null)))
      .thenCompose(r -> r.after(this::lookupItemRelatedRecords))
      .thenCompose(r -> r.after(this::lookupFeeFineOwner))
      .thenCompose(r -> r.after(this::lookupFeeFine))
      .thenCompose(r -> r.after(params -> {
        if (params.item == null || params.feeFineOwner == null || params.feeFine == null) {
          return completedFuture(succeeded(null));
        }

        AccountStorageRepresentation account = new AccountStorageRepresentation(params.loan,
          params.item, params.feeFineOwner, params.feeFine, fineAmount);
        return accountRepository.create(account)
            .thenApply(mapResult(a -> null));
      }));
  }

  private static class CalculationParameters {
    final Loan loan;
    final Item item;
    final FeeFineOwner feeFineOwner;
    final FeeFine feeFine;

    CalculationParameters(Loan loan, Item item, FeeFineOwner feeFineOwner, FeeFine feeFine) {
      this.loan = loan;
      this.item = item;
      this.feeFineOwner = feeFineOwner;
      this.feeFine = feeFine;
    }

    CalculationParameters withItem(Item item) {
      return new CalculationParameters(this.loan, item, this.feeFineOwner, this.feeFine);
    }

    CalculationParameters withOwner(FeeFineOwner feeFineOwner) {
      return new CalculationParameters(this.loan, this.item, feeFineOwner, this.feeFine);
    }

    CalculationParameters withFeeFine(FeeFine feeFine) {
      return new CalculationParameters(this.loan, this.item, this.feeFineOwner, feeFine);
    }
  }

  enum Scenario {
    CHECKIN(OverdueFinePolicy::isUnknown),
    RENEWAL(policy -> policy.isUnknown() || isTrue(policy.getForgiveFineForRenewals()));

    private Predicate<OverdueFinePolicy> shouldNotCreateFine;

    Scenario(Predicate<OverdueFinePolicy> shouldNotCreateFine) {
      this.shouldNotCreateFine = shouldNotCreateFine;
    }
  }
}
