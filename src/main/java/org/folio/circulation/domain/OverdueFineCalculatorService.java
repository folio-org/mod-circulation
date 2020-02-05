package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.OverdueFinePolicy.OverdueFineInterval;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.domain.representations.AccountRepresentation;
import org.folio.circulation.domain.representations.FeeFineRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ResultBinding;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class OverdueFineCalculatorService {
  private static class CalculationParameters {
    Loan loan;
    Item item;
    FeeFineOwner feeFineOwner;
    FeeFine feeFine;

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

  private static final String OVERDUE_FINE_TYPE = "Overdue fine";
  private static final Map<OverdueFineInterval, Integer> MINUTES_IN_INTERVAL = new HashMap<>();
  static {
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.MINUTE, 1);
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.HOUR, 60);
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.DAY, 1440);
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.WEEK, 10080);
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.MONTH, 44640);
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.YEAR, 525600);
  }

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

  public CompletableFuture<Result<CheckInProcessRecords>> calculateOverdueFine(
    CheckInProcessRecords records) {
    Loan loan = records.getLoan();
    if (loan == null) {
      return completedFuture(of(() -> records));
    }

    return calculateOverdueFine(loan).thenApply(mapResult(v -> records));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> calculateOverdueFine(
    LoanAndRelatedRecords records) {
    Loan loan = records.getLoan();
    if (loan == null) {
      return completedFuture(of(() -> records));
    }
    return calculateOverdueFine(loan).thenApply(mapResult(v -> records));
  }

  private CompletableFuture<Result<Account>> calculateOverdueFine(Loan loan) {
    return completedFuture(succeeded(loan))
      .thenComposeAsync(overdueFinePolicyRepository::findOverdueFinePolicyForLoan)
      .thenCompose(r -> r.after(l -> getOverdueMinutes(l)
          .thenCompose(mr -> mr.after(minutes -> calculateOverdueFine(l, minutes)))
          .thenCompose(fr -> fr.after(fine -> this.createFeeFineRecord(l, fine)))
      ));
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan) {
    DateTime systemTime = loan.getReturnDate();
    if (systemTime == null) {
      systemTime = DateTime.now(DateTimeZone.UTC);
    }
    return overduePeriodCalculatorService.getMinutes(loan, systemTime);
  }

  private CompletableFuture<Result<Double>> calculateOverdueFine(Loan loan, Integer overdueMinutes) {
    double maxFine = loan.wasDueDateChangedByRecall() ?
      loan.getOverdueFinePolicy().getMaxOverdueRecallFine() :
      loan.getOverdueFinePolicy().getMaxOverdueFine();
    OverdueFineInterval interval = loan.getOverdueFinePolicy().getOverdueFineInterval();
    double overdueFine = loan.getOverdueFinePolicy().getOverdueFine() *
      Math.ceil(overdueMinutes.doubleValue()/MINUTES_IN_INTERVAL.get(interval).doubleValue());
    if (maxFine > 0) {
      overdueFine = Math.min(overdueFine, maxFine);
    }
    return CompletableFuture.completedFuture(succeeded(overdueFine));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupItemRelatedRecords(
    CalculationParameters params) {
    return itemRepository.fetchItemRelatedRecords(succeeded(params.loan.getItem()))
      .thenApply(ResultBinding.mapResult(params::withItem));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFineOwner(
    CalculationParameters params) {
    return feeFineOwnerRepository.getFeeFineOwner(
      params.item.getLocation().getPrimaryServicePointId().toString())
      .thenApply(ResultBinding.mapResult(params::withOwner));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFine(
    CalculationParameters params) {
    if (params.feeFineOwner == null) {
      return completedFuture(succeeded(params));
    }
    return feeFineRepository.getFeeFine(params.feeFineOwner.getId(), OVERDUE_FINE_TYPE)
      .thenApply(ResultBinding.mapResult(params::withFeeFine))
      .thenCompose(r -> r.after(updatedParams -> {
        if (updatedParams.feeFine == null) {
          FeeFineRepresentation feeFineRepresentation = new FeeFineRepresentation(
            updatedParams.feeFineOwner.getId(), OVERDUE_FINE_TYPE
          );
          return feeFineRepository.create(feeFineRepresentation)
            .thenApply(ResultBinding.mapResult(params::withFeeFine));
        }
        return completedFuture(succeeded(updatedParams));
      }));
  }

  private CompletableFuture<Result<Account>> createFeeFineRecord(
    Loan loan, Double fineAmount) {
    if (fineAmount <= 0) {
      return failure();
    }

    return CompletableFuture.completedFuture(succeeded(
      new CalculationParameters(loan, null, null, null)))
      .thenCompose(r -> r.after(this::lookupItemRelatedRecords))
      .thenCompose(r -> r.after(this::lookupFeeFineOwner))
      .thenCompose(r -> r.after(this::lookupFeeFine))
      .thenCompose(r -> r.after(params -> {
        if (params.item == null || params.feeFineOwner == null || params.feeFine == null) {
          return failure();
        }

        AccountRepresentation accountRepresentation =
          new AccountRepresentation(params.loan, params.item, params.feeFineOwner,
            params.feeFine, fineAmount);
        return accountRepository.create(accountRepresentation);
      }));
  }

  private CompletableFuture<Result<Account>> failure() {
    return completedFuture(succeeded(new Account(null)));
  }
}
