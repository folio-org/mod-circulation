package org.folio.circulation.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import java.util.function.Predicate;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.policy.Charge;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.LostItemPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.representations.AccountStorageRepresentation;
import org.folio.circulation.domain.representations.FeeFineActionStorageRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class LostItemFeeProcess {

  public static LostItemFeeProcess using(Clients clients) {
    return new LostItemFeeProcess(clients);
  }

  private final Repos repos;
  private final OverduePeriodCalculatorService overduePeriodCalculatorService;

  public LostItemFeeProcess(Repos repos,
          OverduePeriodCalculatorService overduePeriodCalculatorService) {
    this.repos = repos;
    this.overduePeriodCalculatorService = overduePeriodCalculatorService;
  }

  private LostItemFeeProcess(Clients clients) {
    //validar si puedo quitar algunos repositorios
    this(
            new Repos(new OverdueFinePolicyRepository(clients),
                    new AccountRepository(clients),
                    new ItemRepository(clients, true, false, false),
                    new FeeFineOwnerRepository(clients),
                    new FeeFineRepository(clients),
                    new FeeFineActionRepository(clients),
                    new LostItemPolicyRepository(clients)),
            new OverduePeriodCalculatorService(new CalendarRepository(clients),
                    new LoanPolicyRepository(clients))
    );
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> processLostItemFee(LoanAndRelatedRecords records) {
    Loan loan = records.getLoan();
    if (loan == null) {
      return completedFuture(succeeded(records));
    }

    return createLostItemFeeIfNecessary(loan).thenApply(mapResult(v -> records));
  }

  private CompletableFuture<Result<Loan>> createLostItemFeeIfNecessary(Loan loan) {
    Result<Loan> loanResult = succeeded(loan);

    if (loan != null && ((loan.getLostItemHasBeenBilled() != null)
            || (loan.getLostItemHasBeenBilled().equalsIgnoreCase("true")))) {
      return completedFuture(loanResult);
    }

    if (loan != null && ((loan.getLostItemHasBeenBilled() != null)
            || (loan.getLostItemHasBeenBilled().equalsIgnoreCase("false")))) {
      if (loan != null && loan.getReturnDate() != null) {
        loan.updateBilledLostItemFlagForLoan("");
        return completedFuture(loanResult);
      }
      //AGE TO LOST
      return repos.lostItemPolicyRepository.findLostItemPolicyForLoan(loanResult)
              .thenCompose(r -> r.afterWhen(
              l -> shouldCreateLostItemFee(l, ScenarioProcess.AGE_TO_LOST),
              l -> createLostItemFee(l, ScenarioProcess.AGE_TO_LOST).thenApply(mapResult(res -> loan)),
              //  l -> createLostItemProcessingFee(l).thenApply(mapResult(res->loan)),
              l -> completedFuture(loanResult)));
    }

    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    if (loan.getDateLostItemShouldBeBilled().isAfter(systemTime)) {
      loan.updateBilledLostItemFlagForLoan("true");
      loan.updateDateBilledLostItemForLoan(null);
    }
    //PATRON_BILLING_IMMEDIATLY
    return repos.lostItemPolicyRepository.findLostItemPolicyForLoan(loanResult)
            .thenCompose(r -> r.afterWhen(
            l -> shouldCreateLostItemFee(l, ScenarioProcess.PATRON_BILLING_IMMEDIATELY),
            l -> createLostItemFee(l, ScenarioProcess.AGE_TO_LOST).thenApply(mapResult(res -> loan)),
            //  l -> createLostItemProcessingFee(l).thenApply(mapResult(res->loan)),
            l -> completedFuture(loanResult)));
  }

  private CompletableFuture<Result<Loan>> createLostItemFeeIfNecessary(Loan loan,
          ScenarioProcess scenario) {

    Result<Loan> loanResult = succeeded(loan);

    return repos.overdueFinePolicyRepository.findOverdueFinePolicyForLoan(loanResult)
            .thenCompose(r -> r.afterWhen(
            l -> shouldCreateLostItemFee(l, scenario),
            l -> createLostItemFee(l, scenario).thenApply(mapResult(res -> loan)),
            l -> completedFuture(loanResult)));
  }

  private CompletableFuture<Result<Boolean>> shouldCreateLostItemFee(Loan loan, ScenarioProcess scenario) {
    return completedFuture(succeeded(
            scenario.shouldCreateFee.test(loan)
            && LostItemBilledUtils.shouldCreateLostItemFee(loan.getLostItemPolicy())));
  }

  private CompletableFuture<Result<Boolean>> shouldCreateLostItemProcessingFee(Loan loan, ScenarioProcess scenario) {
    return completedFuture(succeeded(
            scenario.shouldCreateFee.test(loan)
            && LostItemBilledUtils.shouldCreateLostItemFee(loan.getLostItemPolicy())));
  }

  private CompletableFuture<Result<Void>> createLostItemFee(Loan loan, ScenarioProcess scenario) {

    return completedFuture(loan.getLostItemPolicy().getChargeAmountItem().getAmount())
            .thenCompose(fine -> createFeeFineRecord(loan, fine));
  }

  private CompletableFuture<Result<Void>> createLostItemProcessingFee(Loan loan, ScenarioProcess scenario) {
    return completedFuture((loan.getLostItemPolicy().getLostItemProcessingFee()))
            .thenCompose(fine -> createFeeFineRecord(loan, fine));
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan) {
    DateTime systemTime = loan.getReturnDate();
    if (systemTime == null) {
      systemTime = DateTime.now(DateTimeZone.UTC);
    }
    return overduePeriodCalculatorService.getMinutes(loan, systemTime);
  }

  private CompletableFuture<Result<Boolean>> continueAgeToLostProcess(Loan loan, Integer overdueAgeToLostMinutes) {
    Boolean itemAgeToLost = false;
    LostItemPolicy lostItemPolicy = loan.getLostItemPolicy();
    if (overdueAgeToLostMinutes > 0 && lostItemPolicy != null) {
      Period patronBilledAfterAgedLost = lostItemPolicy.getPatronBilledAfterAgedLost();

      if (patronBilledAfterAgedLost != null) {

        Integer intervalMinutes = patronBilledAfterAgedLost.toMinutes();

        if (overdueAgeToLostMinutes > intervalMinutes) {
          itemAgeToLost = true;
        }
      }
    }
    return CompletableFuture.completedFuture(succeeded(itemAgeToLost));
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

  private CompletableFuture<Result<CalculationParameters>> lookupLostItemFee(
          CalculationParameters params) {

    return repos.feeFineRepository.getFeeFine(FeeFine.LOST_ITEM_FEE_TYPE, true)
            .thenApply(mapResult(params::withFeeFine));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupLostItemProcessingFee(
          CalculationParameters params) {

    return repos.feeFineRepository.getFeeFine(FeeFine.LOST_ITEM_PROC_FEE_TYPE, true)
            .thenApply(mapResult(params::withFeeFine));
  }

  private CompletableFuture<Result<Void>> createFeeFineRecord(Loan loan, Double fineAmount) {

    if (fineAmount <= 0) {
      return completedFuture(succeeded(null));
    }

    return completedFuture(succeeded(
            new CalculationParameters(loan)))
            .thenCompose(r -> r.after(this::lookupLostItemFee))
            .thenCompose(r -> r.after(this::lookupItemRelatedRecords))
            .thenCompose(r -> r.after(this::lookupFeeFineOwner))
            .thenCompose(r -> r.after(params -> createAccount(fineAmount, params)));
  }

  private CompletableFuture<Result<Void>> createAccount(Double fineAmount,
          CalculationParameters params) {

    if (params.isIncomplete()) {
      return completedFuture(succeeded(null));
    }

    AccountStorageRepresentation accountRepresentation
            = new AccountStorageRepresentation(params.loan, params.item, params.feeFineOwner,
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

  enum ScenarioProcess {
    AGE_TO_LOST(loan -> loan.getLostItemHasBeenBilled().equals("false")),
    PATRON_BILLED_AFTER(loan -> loan.getLostItemPolicy().getPatronBilledAfterAgedLost().toMinutes() > 0),
    PATRON_BILLING_IMMEDIATELY(loan -> loan.getLostItemPolicy().getPatronBilledAfterAgedLost().toMinutes() == 0);

    private Predicate<Loan> shouldCreateFee;

    ScenarioProcess(Predicate<Loan> shouldCreateFee) {
      this.shouldCreateFee = shouldCreateFee;
    }
  }

  public static class LostItemBilledUtils {

    private static boolean shouldCreateLostItemProcessingFee(LostItemPolicy policy) {

      Predicate<LostItemPolicy> p1 = (LostItemPolicy p) -> p.getChargeAmountItem().getChargeType().equals(Charge.ACTUAL_COST);
      Predicate<LostItemPolicy> p2 = (LostItemPolicy p) -> (p.getChargeAmountItem().getAmount() > 0);
      Predicate<LostItemPolicy> p3 = (LostItemPolicy p) -> p.getChargeAmountItemPatron();
      Predicate<LostItemPolicy> p4 = (LostItemPolicy p) -> p.getChargeAmountItemSystem();
      Predicate<LostItemPolicy> ptotal = p1.and(p3).and(p4).or(p2.and(p3).and(p4));

      return ptotal.test(policy);
    }

    private static boolean shouldCreateLostItemFee(LostItemPolicy policy) {

      Predicate<LostItemPolicy> p1 = (LostItemPolicy p) -> (p.getChargeAmountItem().getAmount() > 0);
      Predicate<LostItemPolicy> p2 = (LostItemPolicy p) -> p.getChargeAmountItemPatron();
      //Predicate<LostItemPolicy> p3 = (LostItemPolicy p)-> p.getChargeAmountItemSystem();
      Predicate<LostItemPolicy> ptotal = p1.and(p2);

      return ptotal.test(policy);
    }

  }

  public static class Repos {

    private final OverdueFinePolicyRepository overdueFinePolicyRepository;
    private final AccountRepository accountRepository;
    private final ItemRepository itemRepository;
    private final FeeFineOwnerRepository feeFineOwnerRepository;
    private final FeeFineRepository feeFineRepository;
    private final FeeFineActionRepository feeFineActionRepository;
    private final LostItemPolicyRepository lostItemPolicyRepository;

    Repos(OverdueFinePolicyRepository overdueFinePolicyRepository,
            AccountRepository accountRepository, ItemRepository itemRepository,
            FeeFineOwnerRepository feeFineOwnerRepository, FeeFineRepository feeFineRepository,
            FeeFineActionRepository feeFineActionRepository,
            LostItemPolicyRepository lostItemPolicyRepository) {

      this.overdueFinePolicyRepository = overdueFinePolicyRepository;
      this.accountRepository = accountRepository;
      this.itemRepository = itemRepository;
      this.feeFineOwnerRepository = feeFineOwnerRepository;
      this.feeFineRepository = feeFineRepository;
      this.feeFineActionRepository = feeFineActionRepository;
      this.lostItemPolicyRepository = lostItemPolicyRepository;
    }
  }
}
