package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.OverdueFinePolicy.OverdueFineInterval;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.support.*;
import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

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

  private static final String overdueFineType = "Overdue fine";
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

  private Clients clients;
  private OverdueFinePolicyRepository overdueFinePolicyRepository;
  private AccountRepository accountRepository;
  private ItemRepository itemRepository;
  private FeeFineOwnerRepository feeFineOwnerRepository;
  private FeeFineRepository feeFineRepository;

  private OverdueFineCalculatorService(Clients clients) {
    this.clients = clients;
    overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    accountRepository = new AccountRepository(clients);
    itemRepository = new ItemRepository(clients, true, false, false);
    feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    feeFineRepository = new FeeFineRepository(clients);
  }

  public CompletableFuture<Result<CheckInProcessRecords>> calculateOverdueFine(
    CheckInProcessRecords records) {
    Loan loan = records.getLoan();
    if (loan == null) {
      return completedFuture(of(() -> records));
    }

    return calculateOverdueFine(loan, clients).thenApply(mapResult(v -> records));
  }

  private CompletableFuture<Result<Account>> calculateOverdueFine(Loan loan, Clients clients) {
    return completedFuture(succeeded(loan))
      .thenComposeAsync(overdueFinePolicyRepository::findOverdueFinePolicyForLoan)
      .thenCompose(r -> r.after(l -> getOverdueMinutes(l, clients)
          .thenCompose(mr -> mr.after(minutes -> calculateOverdueFine(l, minutes)))
          .thenCompose(fr -> fr.after(fine -> this.createFeeFineRecord(l, fine)))
      ));
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, Clients clients) {
    return OverduePeriodCalculatorService.using(clients).getMinutes(loan, DateTime.now());
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
    return feeFineRepository.getFeeFine(params.feeFineOwner.getId(), overdueFineType)
      .thenApply(ResultBinding.mapResult(params::withFeeFine));
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
