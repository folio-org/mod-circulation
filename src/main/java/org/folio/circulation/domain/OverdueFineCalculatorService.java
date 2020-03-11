package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.OverdueFineCalculationParameters;
import org.folio.circulation.domain.policy.OverdueFineInterval;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.domain.representations.AccountStorageRepresentation;
import org.folio.circulation.domain.representations.FeeFineActionStorageRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ResultBinding;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class OverdueFineCalculatorService {
  public static OverdueFineCalculatorService using(Clients clients) {
    return new OverdueFineCalculatorService(clients);
  }

  private final Repos repos;
  private final OverduePeriodCalculatorService overduePeriodCalculatorService;

  public OverdueFineCalculatorService(Repos repos,
    OverduePeriodCalculatorService overduePeriodCalculatorService) {
    this.repos = repos;
    this.overduePeriodCalculatorService = overduePeriodCalculatorService;
  }

  private OverdueFineCalculatorService(Clients clients) {
    this(
      new Repos(new OverdueFinePolicyRepository(clients),
        new AccountRepository(clients),
        new ItemRepository(clients, true, false, false),
        new FeeFineOwnerRepository(clients),
        new FeeFineRepository(clients),
        new UserRepository(clients),
        new FeeFineActionRepository(clients)),
      new OverduePeriodCalculatorService(new CalendarRepository(clients),
        new LoanPolicyRepository(clients))
    );
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> createOverdueFineIfNecessary(
    LoanAndRelatedRecords records, WebContext context) {

    return createOverdueFineIfNecessary(records.getLoan(), Scenario.RENEWAL, context.getUserId())
      .thenApply(mapResult(r -> records));
  }

  public CompletableFuture<Result<CheckInProcessRecords>> createOverdueFineIfNecessary(
    CheckInProcessRecords records, WebContext context) {

    return createOverdueFineIfNecessary(records.getLoan(), Scenario.CHECKIN, context.getUserId())
      .thenApply(mapResult(r -> records));
  }

  public CompletableFuture<Result<Loan>> createOverdueFineIfNecessary(Loan loan,
    Scenario scenario, String loggedInUserId) {

    Result<Loan> loanResult = succeeded(loan);
    CompletableFuture<Result<Loan>> doNothing = completedFuture(loanResult);

    if (loan == null || !loan.isOverdue()) {
      return doNothing;
    }

    return repos.overdueFinePolicyRepository.findOverdueFinePolicyForLoan(loanResult)
      .thenCompose(r -> r.afterWhen(
        l -> shouldCreateFine(l, scenario),
        l -> createOverdueFine(l, loggedInUserId).thenApply(mapResult(res -> loan)),
        l -> doNothing));
  }

  private CompletableFuture<Result<Boolean>> shouldCreateFine(Loan loan, Scenario scenario) {
    return completedFuture(succeeded(
      scenario.shouldCreateFine.test(loan.getOverdueFinePolicy())));
  }

  private CompletableFuture<Result<Void>> createOverdueFine(Loan loan, String loggedInUserId) {
    return getOverdueMinutes(loan)
      .thenCompose(r -> r.after(minutes -> calculateOverdueFine(loan, minutes)))
      .thenCompose(r -> r.after(fine -> createFeeFineRecord(loan, fine, loggedInUserId)));
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
      .map(id -> repos.feeFineOwnerRepository.getFeeFineOwner(id)
        .thenApply(mapResult(params::withOwner)))
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

  private CompletableFuture<Result<Void>> createFeeFineRecord(Loan loan, Double fineAmount,
    String loggedInUserId) {

    if (fineAmount <= 0) {
      return completedFuture(succeeded(null));
    }

    return completedFuture(succeeded(
      new CalculationParameters(loan)))
      .thenCompose(r -> r.after(this::lookupFeeFine))
      .thenCompose(r -> r.after(this::lookupItemRelatedRecords))
      .thenCompose(r -> r.after(this::lookupFeeFineOwner))
      .thenCompose(r -> r.after(params -> this.lookupLoggedInUser(params, loggedInUserId)))
      .thenCompose(r -> r.after(params -> createAccount(fineAmount, params)));
  }

  private CompletableFuture<Result<Void>> createAccount(Double fineAmount,
    CalculationParameters params) {

    if (params.isIncomplete()) {
      return completedFuture(succeeded(null));
    }

    AccountStorageRepresentation accountRepresentation =
      new AccountStorageRepresentation(params.loan, params.item, params.feeFineOwner,
        params.feeFine, fineAmount);

    return repos.accountRepository.create(accountRepresentation)
      .thenCompose(rac -> rac.after(account -> this.createFeeFineAction(account, params)
        .thenApply(rfa -> rfa.map(feeFineAction -> null))));
  }

  private CompletableFuture<Result<FeeFineAction>> createFeeFineAction(
    Account account, CalculationParameters params) {

    return repos.feeFineActionRepository.create(new FeeFineActionStorageRepresentation(account,
      params.feeFineOwner, params.feeFine, account.getAmount(), account.getAmount(),
      params.loggedInUser));
  }

  private static class CalculationParameters {
    final Loan loan;
    final Item item;
    final FeeFineOwner feeFineOwner;
    final FeeFine feeFine;
    final User loggedInUser;

    CalculationParameters(Loan loan) {
      this(loan, null, null, null, null);
    }

    CalculationParameters(Loan loan, Item item, FeeFineOwner feeFineOwner, FeeFine feeFine,
      User loggedInUser) {

      this.loan = loan;
      this.item = item;
      this.feeFineOwner = feeFineOwner;
      this.feeFine = feeFine;
      this.loggedInUser = loggedInUser;
    }

    CalculationParameters withItem(Item item) {
      return new CalculationParameters(this.loan, item, this.feeFineOwner, this.feeFine,
        this.loggedInUser);
    }

    CalculationParameters withOwner(FeeFineOwner feeFineOwner) {
      return new CalculationParameters(this.loan, this.item, feeFineOwner, this.feeFine,
        this.loggedInUser);
    }

    CalculationParameters withFeeFine(FeeFine feeFine) {
      return new CalculationParameters(this.loan, this.item, this.feeFineOwner, feeFine,
        this.loggedInUser);
    }

    CalculationParameters withLoggedInUser(User loggedInUser) {
      return new CalculationParameters(this.loan, this.item, this.feeFineOwner, this.feeFine,
        loggedInUser);
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

    private Predicate<OverdueFinePolicy> shouldCreateFine;

    Scenario(Predicate<OverdueFinePolicy> shouldCreateFine) {
      this.shouldCreateFine = shouldCreateFine;
    }
  }

  public static class Repos {
    private final OverdueFinePolicyRepository overdueFinePolicyRepository;
    private final AccountRepository accountRepository;
    private final ItemRepository itemRepository;
    private final FeeFineOwnerRepository feeFineOwnerRepository;
    private final FeeFineRepository feeFineRepository;
    private final UserRepository userRepository;
    private final FeeFineActionRepository feeFineActionRepository;

    Repos(OverdueFinePolicyRepository overdueFinePolicyRepository,
      AccountRepository accountRepository, ItemRepository itemRepository,
      FeeFineOwnerRepository feeFineOwnerRepository, FeeFineRepository feeFineRepository,
      UserRepository userRepository, FeeFineActionRepository feeFineActionRepository) {

      this.overdueFinePolicyRepository = overdueFinePolicyRepository;
      this.accountRepository = accountRepository;
      this.itemRepository = itemRepository;
      this.feeFineOwnerRepository = feeFineOwnerRepository;
      this.feeFineRepository = feeFineRepository;
      this.userRepository = userRepository;
      this.feeFineActionRepository = feeFineActionRepository;
    }
  }
}
