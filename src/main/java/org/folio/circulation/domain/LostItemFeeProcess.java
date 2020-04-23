package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import org.folio.circulation.domain.policy.Charge;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.OverdueFineInterval;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.LostItemPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.representations.AccountStorageRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ResultBinding;
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
                    new UserRepository(clients),
                    new LostItemPolicyRepository(clients)),
            new OverduePeriodCalculatorService(new CalendarRepository(clients),
                    new LoanPolicyRepository(clients))
    );
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> processLostItemFee(LoanAndRelatedRecords records) {
    Loan loan = records.getLoan();
    if (loan == null) {
      return completedFuture(of(() -> records));
    }

    return createLostItemFeeIfNecessary(loan).thenApply(mapResult(v -> records));
  }

  //Este metodo enviará a los posibles escenarios
  private CompletableFuture<Result<Loan>> createLostItemFeeIfNecessary(Loan loan) {
    Result<Loan> loanResult = succeeded(loan);

    if (loan != null && ((loan.getLostItemHasBeenBilled() != null)
            || (loan.getLostItemHasBeenBilled().equalsIgnoreCase("true")))) {
      return completedFuture(loanResult);
    }

    //cuando es falsa entra a escenario AGE TO LOST
    if (loan != null && ((loan.getLostItemHasBeenBilled() != null)
            || (loan.getLostItemHasBeenBilled().equalsIgnoreCase("false")))) {
      if (loan != null && loan.getReturnDate() != null) {
        loan.updateBilledLostItemFlagForLoan("");
        return completedFuture(loanResult);
      }
      return repos.lostItemPolicyRepository.findLostItemPolicyForLoan(loanResult)
              .thenCompose(r -> r.afterWhen(
              l -> shouldCreateFee(l, ScenarioFee.AGE_TO_LOST),
              l -> createLostItemFee(l).thenApply(mapResult(res -> loan)),
              l -> completedFuture(loanResult)));
    }

    //Aquí debe validar si es AFTER o IMMEDIATLY
    return repos.lostItemPolicyRepository.findLostItemPolicyForLoan(loanResult)
            .thenCompose(r -> r.afterWhen(
            l -> shouldCreateFee(l, ScenarioFee.PATRON_BILLED_AFTER),
            l -> createLostItemFee(l).thenApply(mapResult(res -> loan)),
            l -> completedFuture(loanResult)));
  }

  private CompletableFuture<Result<Boolean>> shouldCreateFee(Loan loan, ScenarioFee scenario) {
    return completedFuture(succeeded(
            //test de predicates
            scenario.shouldCreateFee.test(loan.getLostItemPolicy())));
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan) {
    DateTime systemTime = loan.getReturnDate();
    if (systemTime == null) {
      systemTime = DateTime.now(DateTimeZone.UTC);
    }
    return overduePeriodCalculatorService.getMinutes(loan, systemTime);
  }

  private CompletableFuture<Result<Loan>> createLostItemFee(Loan loan) {
      //primera condición del AGE TO LOST
        DateTime systemTime = DateTime.now(DateTimeZone.UTC);
        if (loan.getDateLostItemShouldBeBilled().isAfter(systemTime)) {
          loan.updateBilledLostItemFlagForLoan("true");
          loan.updateDateBilledLostItemForLoan(null);
          //Falta salvar                  
          //if (){          
        }
      
    
    
    return overduePeriodCalculatorService.getMinutes(loan, systemTime);
  }

  private CompletableFuture<Result<String>> validateLostItemFeeType(Loan loan) {
    String chargeTypeLostItemFee = "";
    LostItemPolicy lostItemPolicy = loan.getLostItemPolicy();
    if (lostItemPolicy != null && lostItemPolicy.getChargeAmountItemPatron()) {
      if (lostItemPolicy.getChargeAmountItem().getChargeType().equals(Charge.ACTUAL_COST)) {
        if (lostItemPolicy.getChargeAmountItemSystem()) {
          createFeeFineRecord(loan, Double.NaN);
        } else {
        }
      }
    }
    return CompletableFuture.completedFuture(succeeded(chargeTypeLostItemFee));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupItemRelatedRecords(CalculationParameters params) {
    return repos.itemRepository.fetchItemRelatedRecords(succeeded(params.loan.getItem())).thenApply(ResultBinding.mapResult(params::withItem));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFineOwner(CalculationParameters params) {
    return repos.feeFineOwnerRepository.getFeeFineOwner(params.item.getLocation().getPrimaryServicePointId().toString()).thenApply(ResultBinding.mapResult(params::withOwner));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupLostItemFee(CalculationParameters params) {
    if (params.feeFineOwner == null) {
      return completedFuture(succeeded(params));
    }
    //buscar la lostItemFee
    return repos.feeFineRepository.getOverdueFine(params.feeFineOwner.getId())
            .thenApply(ResultBinding.mapResult(params::withFeeFine))
            .thenCompose(r -> r.after(updatedParams -> {
      if (updatedParams.feeFine == null) {
        FeeFine feeFine = new FeeFine(UUID.randomUUID().toString(), updatedParams.feeFineOwner.getId(), FeeFine.LOST_ITEM_FEE_TYPE);
        return repos.feeFineRepository.create(feeFine)
                .thenApply(ResultBinding.mapResult(params::withFeeFine));
      }
      return completedFuture(succeeded(updatedParams));
    }));
  }

  private CompletableFuture<Result<Account>> createFeeFineRecord(Loan loan, Double fineAmount) {
    if (fineAmount <= 0) {
      return failure();
    }
    return CompletableFuture.completedFuture(succeeded(new CalculationParameters(loan, null, null, null))).thenCompose(r -> r.after(this::lookupItemRelatedRecords)).thenCompose(r -> r.after(this::lookupFeeFineOwner)).thenCompose(r -> r.after(this::lookupLostItemFee)).thenCompose(r -> r.after(params -> {
      if (params.item == null || params.feeFineOwner == null || params.feeFine == null) {
        return failure();
      }
      AccountStorageRepresentation account = new AccountStorageRepresentation(params.loan, params.item, params.feeFineOwner, params.feeFine, fineAmount);
      return repos.accountRepository.create(account);
    }));
  }

  private CompletableFuture<Result<Account>> failure() {
    return completedFuture(succeeded(Account.from(null)));
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

  enum ScenarioFee {
    AGE_TO_LOST(loan -> loan.getLostItemHasBeenBilled().equals("false")),
    PATRON_BILLED_AFTER(loan -> loan.getLostItemPolicy().getPatronBilledAfterAgedLost().toMinutes() > 0),
    PATRON_BILLING_IMMEDIATELY(loan -> loan.getLostItemPolicy().getPatronBilledAfterAgedLost().toMinutes() == 0);

    private Predicate<Loan> shouldCreateFee;

    ScenarioFee(Predicate<Loan> shouldCreateFee) {
      this.shouldCreateFee = shouldCreateFee;
    }
  }

  public static class lostItemBilledUtils {

    public static Predicate<Loan> billedFilter() {
      return (Loan l) -> {
        return l.getLostItemHasBeenBilled().equals("true");
      };
    }

    public static Predicate<Loan> returnedFilter() {
      return (Loan l) -> {
        return (l.getReturnDate() != null);
      };
    }

    public static boolean shouldCreateLostItemProcessingFee(LostItemPolicy policy) {

      Predicate<LostItemPolicy> p1 = (LostItemPolicy p) -> p.getChargeAmountItem().getChargeType().equals(Charge.ACTUAL_COST);
      Predicate<LostItemPolicy> p2 = (LostItemPolicy p) -> (p.getChargeAmountItem().getAmount() > 0);
      Predicate<LostItemPolicy> p3 = (LostItemPolicy p) -> p.getChargeAmountItemPatron();
      Predicate<LostItemPolicy> p4 = (LostItemPolicy p) -> p.getChargeAmountItemSystem();
      Predicate<LostItemPolicy> ptotal = p1.and(p3).and(p4).or(p2.and(p3).and(p4));

      return ptotal.test(policy);
    }

    public static boolean shouldCreateLostItemFee(LostItemPolicy policy) {

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
    private final UserRepository userRepository;
    private final LostItemPolicyRepository lostItemPolicyRepository;

    Repos(OverdueFinePolicyRepository overdueFinePolicyRepository,
            AccountRepository accountRepository, ItemRepository itemRepository,
            FeeFineOwnerRepository feeFineOwnerRepository, FeeFineRepository feeFineRepository,
            UserRepository userRepository, LostItemPolicyRepository lostItemPolicyRepository) {

      this.overdueFinePolicyRepository = overdueFinePolicyRepository;
      this.accountRepository = accountRepository;
      this.itemRepository = itemRepository;
      this.feeFineOwnerRepository = feeFineOwnerRepository;
      this.feeFineRepository = feeFineRepository;
      this.userRepository = userRepository;
      this.lostItemPolicyRepository = lostItemPolicyRepository;
    }
  }
}
