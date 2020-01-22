package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.policy.OverdueFinePolicy.OverdueFineInterval;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.Result.succeeded;

public class CalculateOverdueFineResource extends Resource {
  class CalculationParameters {
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

    public Loan getLoan() {
      return loan;
    }

    public Item getItem() {
      return item;
    }

    public FeeFineOwner getFeeFineOwner() {
      return feeFineOwner;
    }

    public FeeFine getFeeFine() {
      return feeFine;
    }

    CalculationParameters withLoan(Loan loan) {
      return new CalculationParameters(loan, this.item, this.feeFineOwner, this.feeFine);
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

  private LoanRepository loanRepository;
  private CalendarRepository calendarRepository;
  private OverdueFinePolicyRepository overdueFinePolicyRepository;
  private AccountRepository accountRepository;
  private ItemRepository itemRepository;
  private FeeFineOwnerRepository feeFineOwnerRepository;
  private FeeFineRepository feeFineRepository;

  private static final Map<OverdueFineInterval, Integer> MINUTES_IN_INTERVAL = new HashMap<>();
  static {
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.HOUR, 60);
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.DAY, 1440);
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.WEEK, 10080);
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.MONTH, 44640);
    MINUTES_IN_INTERVAL.put(OverdueFineInterval.YEAR, 525600);
  }

  public CalculateOverdueFineResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    router.post("/circulation/loans/:id/calculate-overdue-fine")
      .handler(this::calculateOverdueFine);
  }

  private void calculateOverdueFine(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    loanRepository = new LoanRepository(clients);
    calendarRepository = new CalendarRepository(clients);
    overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    accountRepository = new AccountRepository(clients);
    itemRepository = new ItemRepository(clients, true, false, false);
    feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    feeFineRepository = new FeeFineRepository(clients);

    String loanId = routingContext.request().getParam("id");

    loanRepository.getById(loanId)
      .thenApply(ResultBinding.mapResult(LoanAndRelatedRecords::new))
      .thenComposeAsync(r -> r.after(overdueFinePolicyRepository::lookupOverdueFinePolicy))
      .thenApply(ResultBinding.mapResult(LoanAndRelatedRecords::getLoan))
      .thenCompose(lr -> lr.after(loan -> getOverdueMinutes(loan, clients)
        .thenCompose(mr -> mr.after(minutes -> calculateOverdueFine(loan, minutes)))
        .thenCompose(r -> r.after(fine -> this.createFeeFineRecord(loan, fine)))
      ))
      .thenApply(NoContentResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Loan loan, Clients clients) {
    //return OverduePeriodCalculator.countMinutes(loan, DateTime.now(), clients);
    return CompletableFuture.completedFuture(succeeded(0));
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
    return itemRepository.fetchItemRelatedRecords(succeeded(params.getItem()))
      .thenApply(ResultBinding.mapResult(params::withItem));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFineOwner(
    CalculationParameters params) {
    return feeFineOwnerRepository.getFeeFineOwner(
      params.getItem().getLocation().getPrimaryServicePointId().toString())
      .thenApply(ResultBinding.mapResult(params::withOwner));
  }

  private CompletableFuture<Result<CalculationParameters>> lookupFeeFine(
    CalculationParameters params) {
    return feeFineRepository.getFeeFine(
      params.getItem().getLocation().getPrimaryServicePointId().toString())
      .thenApply(ResultBinding.mapResult(params::withFeeFine));
  }

  private CompletableFuture<Result<Account>> createFeeFineRecord(
    Loan loan, Double fineAmount) {
    return CompletableFuture.completedFuture(succeeded(
      new CalculationParameters(loan, null, null, null)))
      .thenCompose(r -> r.after(this::lookupItemRelatedRecords))
      .thenCompose(r -> r.after(this::lookupFeeFineOwner))
      .thenCompose(r -> r.after(this::lookupFeeFine))
      .thenCompose(r -> r.after(params -> {
        AccountRepresentation accountRepresentation =
          new AccountRepresentation(params.getLoan(), params.getItem(), params.getFeeFineOwner(),
            params.getFeeFine(), fineAmount);
        return accountRepository.create(accountRepresentation);
      }));
  }
}
