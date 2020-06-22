package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.circulation.domain.OverdueFineCalculatorService.Scenario.CHECKIN;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.policy.OverdueFineCalculationParameters;
import org.folio.circulation.domain.policy.OverdueFineInterval;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.domain.representations.StoredAccount;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ResultBinding;
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
        new FeeFineActionRepository(clients),
        new LostItemPolicyRepository(clients)),
      new OverduePeriodCalculatorService(new CalendarRepository(clients),
        new LoanPolicyRepository(clients))
    );
  }

  public CompletableFuture<Result<RenewalContext>> createOverdueFineIfNecessary(
    RenewalContext context) {

    final String loggedInUserId = context.getLoggedInUserId();
    final Loan loanBeforeRenewal = context.getLoanBeforeRenewal();

    return shouldChargeOverdueFineOnRenewal(context)
      .thenCompose(r -> r.afterWhen(ResultBinding.toFutureResult(),
        b -> createOverdueFineIfNecessary(loanBeforeRenewal, Scenario.RENEWAL, loggedInUserId),
        b -> completedFuture(succeeded(null))))
      .thenApply(mapResult(context::withOverdueFeeFineAction));
  }

  private CompletableFuture<Result<Boolean>> shouldChargeOverdueFineOnRenewal(
    RenewalContext renewalContext) {

    Loan loan = renewalContext.getLoanBeforeRenewal();
    if (loan == null || !loan.isOverdue()) {
      return completedFuture(succeeded(null));
    }

    if (isDeclaredLost(renewalContext.getItemStatusBeforeRenewal())) {
      return repos.lostItemPolicyRepository.getLostItemPolicyById(loan.getLostItemPolicyId())
        .thenApply(r -> r.map(policy -> policy.shouldChargeOverdueFee()
          && renewalContext.isLostItemFeesRefundedOrCancelled()));
    }

    return completedFuture(succeeded(true));
  }

  public CompletableFuture<Result<FeeFineAction>> createOverdueFineIfNecessary(
    CheckInProcessRecords records, String userId) {

    return shouldChargeOverdueFineOnCheckIn(records)
      .thenCompose(r -> r.afterWhen(ResultBinding.toFutureResult(),
          b -> createOverdueFineIfNecessary(records.getLoan(), CHECKIN, userId),
          b -> completedFuture(succeeded(null))));
  }

  private CompletableFuture<Result<Boolean>> shouldChargeOverdueFineOnCheckIn(
    CheckInProcessRecords records) {

    final Loan loan = records.getLoan();
    if (loan == null || !loan.isOverdue(loan.getReturnDate())) {
      return completedFuture(succeeded(false));
    }

    if (isDeclaredLost(records.getItemStatusBeforeCheckIn())) {
      return repos.lostItemPolicyRepository.getLostItemPolicyById(loan.getLostItemPolicyId())
        .thenApply(r -> r.map(policy -> policy.shouldChargeOverdueFee()
          && records.areLostItemFeesRefundedOrCancelled()));
    }

    return completedFuture(succeeded(true));
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
      .map(id -> repos.feeFineOwnerRepository.findOwnerForServicePoint(id)
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

  private CompletableFuture<Result<FeeFineAction>> createFeeFineRecord(Loan loan, Double fineAmount,
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

  private CompletableFuture<Result<FeeFineAction>> createAccount(Double fineAmount,
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

    return repos.feeFineActionRepository.create(StoredFeeFineAction.builder()
      .useAccount(account)
      .withAction(account.getFeeFineType())
      .withCreatedAt(params.feeFineOwner.getOwner())
      .withCreatedBy(params.loggedInUser)
      .build());
  }

  private boolean isDeclaredLost(ItemStatus itemStatus) {
    return itemStatus == ItemStatus.DECLARED_LOST;
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

    private final Predicate<OverdueFinePolicy> shouldCreateFine;

    Scenario(Predicate<OverdueFinePolicy> shouldCreateFine) {
      this.shouldCreateFine = shouldCreateFine;
    }

    private boolean shouldCreateFine(OverdueFinePolicy overdueFinePolicy) {
      return shouldCreateFine.test(overdueFinePolicy);
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
    private final LostItemPolicyRepository lostItemPolicyRepository;

    Repos(OverdueFinePolicyRepository overdueFinePolicyRepository,
      AccountRepository accountRepository, ItemRepository itemRepository,
      FeeFineOwnerRepository feeFineOwnerRepository, FeeFineRepository feeFineRepository,
      UserRepository userRepository, FeeFineActionRepository feeFineActionRepository,
      LostItemPolicyRepository lostItemPolicyRepository) {

      this.overdueFinePolicyRepository = overdueFinePolicyRepository;
      this.accountRepository = accountRepository;
      this.itemRepository = itemRepository;
      this.feeFineOwnerRepository = feeFineOwnerRepository;
      this.feeFineRepository = feeFineRepository;
      this.userRepository = userRepository;
      this.feeFineActionRepository = feeFineActionRepository;
      this.lostItemPolicyRepository = lostItemPolicyRepository;
    }
  }
}
