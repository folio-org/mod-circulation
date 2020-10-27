package org.folio.circulation.resources;

import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.http.client.CqlQuery.matchAny;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.ItemLostRequiringActualCostsRepresentation;
import org.folio.circulation.domain.ItemLostRequiringCostsEntry;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class ItemsLostRequiringActualCostResource extends Resource {

  private final String rootPath;

  public ItemsLostRequiringActualCostResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.getMany(this::getMany);
  }

  private void getMany(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    final Repositories repositories = new Repositories(clients);
    final ItemLostRequiringActualCostsRepresentation itemRepresentation =
      new ItemLostRequiringActualCostsRepresentation();

    final Collection<String> fieldValues = List.of(
      DECLARED_LOST.getValue(),
      AGED_TO_LOST.getValue());

    repositories.getItem().findByQuery(matchAny("status.name", fieldValues))
      .thenCompose(r -> r.after(items -> 
        allOf(items, item -> fetchItemLoanAndFeeFine(
          new ItemLostRequiringCostsEntry(item), repositories, itemRepresentation)
        )))
      .thenApply(this::mapResultToJson)
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<JsonObject>> fetchItemLoanAndFeeFine(
    ItemLostRequiringCostsEntry entry, Repositories repositories,
    ItemLostRequiringActualCostsRepresentation itemRepresentation) {

    return CompletableFuture.completedFuture(Result.succeeded(entry))
      .thenCompose(r -> r.after(e -> findOpenLoan(e, repositories)))
      .thenCompose(r -> r.after(e -> mapEntryToJson(e, itemRepresentation)));
  }

  private CompletableFuture<Result<ItemLostRequiringCostsEntry>> findOpenLoan(
    ItemLostRequiringCostsEntry entry, Repositories repositories) {

    return repositories.getLoan().findOpenLoanForItem(entry.getItem())
      .thenComposeAsync(r -> fetchLoanUser(r, repositories.getUser()))
      .thenComposeAsync(repositories.getAccount()::findAccountsAndActionsForLoan)
      .thenComposeAsync(repositories.getServicePoint()::findServicePointsForLoan)
      .thenComposeAsync(repositories.getUser()::findUserForLoan)
      .thenComposeAsync(repositories.getLoanPolicy()::findPolicyForLoan)
      .thenComposeAsync(repositories.getOverdueFinePolicy()::findOverdueFinePolicyForLoan)
      .thenComposeAsync(repositories.getLostItemPolicy()::findLostItemPolicyForLoan)
      .thenComposeAsync(repositories.getPatronGroup()::findGroupForLoan)
      .thenCompose(r -> r.after(loan -> 
        CompletableFuture.completedFuture(Result.succeeded(entry.withLoan(loan)))));
  }

  private CompletableFuture<Result<JsonObject>> mapEntryToJson(ItemLostRequiringCostsEntry entry,
    ItemLostRequiringActualCostsRepresentation itemRepresentation) {

    if (entry.getLoan() != null && entry.getLoan().isOpen()) {
      if (!entry.getItem().getStatusName().equals(AGED_TO_LOST.getValue()) ||
        entry.getLoan().getLostItemHasBeenBilled()) {

        if (hasNoLostFeeFineActualCosts(entry.getLoan())) {
          return Result.ofAsync(() -> itemRepresentation.mapToResult(entry));
        }
      }
    }

    return Result.ofAsync(() -> null);
  }

  private boolean hasNoLostFeeFineActualCosts(Loan loan) {
    if (loan.getAccounts() != null && loan.getAccounts().size() > 0) {
      for (Account account : loan.getAccounts()) {
        if (account.getFeeFineType().equals(FeeFine.LOST_ITEM_FEE_ACTUAL_COSTS_TYPE)) {
          return false;
        }
      }
    }

    return true;
  }

  private Result<JsonObject> mapResultToJson(Result<List<JsonObject>> items) {
    Result<JsonArray> result = items.map(r -> r.stream()
      .filter(Objects::nonNull)
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add)));

    return result.next(jsonArray -> Result.succeeded(new JsonObject()
      .put("items", jsonArray)
      .put("totalRecords", jsonArray.size())));
  }

  private CompletableFuture<Result<Loan>> fetchLoanUser(Result<Loan> result, UserRepository userRepository) {
    return result.combineAfter(userRepository::getUser, Loan::withUser);
  }

  @AllArgsConstructor
  @Getter
  private class Repositories {
    private final ItemRepository item;
    private final LoanRepository loan;
    private final ServicePointRepository servicePoint;
    private final UserRepository user;
    private final LoanPolicyRepository loanPolicy;
    private final OverdueFinePolicyRepository overdueFinePolicy;
    private final LostItemPolicyRepository lostItemPolicy;
    private final AccountRepository account;
    private final PatronGroupRepository patronGroup;
    private final FeeFineActionRepository feeFineAction;

    public Repositories(Clients clients) {
      this(
        new ItemRepository(clients, true, true, true),
        new LoanRepository(clients),
        new ServicePointRepository(clients),
        new UserRepository(clients),
        new LoanPolicyRepository(clients),
        new OverdueFinePolicyRepository(clients),
        new LostItemPolicyRepository(clients),
        new AccountRepository(clients),
        new PatronGroupRepository(clients),
        new FeeFineActionRepository(clients)
      );
    }
  }
}
