package org.folio.circulation.resources;

import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.utils.CollectionUtil.uniqueSetOf;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemLostRequiringActualCostsRepresentation;
import org.folio.circulation.domain.LostItemRequiringCostsFee;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
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

public class LostItemsRequiringActualCostResource extends Resource {
  private static final String RECORD_KEY = "lostItems";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";

  private final String rootPath;

  public LostItemsRequiringActualCostResource(String rootPath, HttpClient client) {
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
    final PageLimit pageLimit = PageLimit.limit(context.getIntegerParameter("limit", 100));
    final Offset offset = Offset.offset(context.getIntegerParameter("offset", 0));

    repositories.getLoan().getMany(buildQuery().value(), pageLimit, offset)
      .thenCompose(r -> r.after(l -> repositories.getLostItemPolicy().findLostItemPoliciesForLoans(l)))
      .thenApply(r -> r.map(this::filterOutLoans))
      .thenComposeAsync(r -> repositories.getItem().fetchItemsFor(r, Loan::withItem))
      .thenComposeAsync(r -> r.after(l -> repositories.getServicePoint().findServicePointsForLoans(l)))
      .thenComposeAsync(r -> r.after(l -> repositories.getUser().findUsersForLoans(l)))
      .thenCompose(r -> r.after(l -> mapToLostItem(l)))
      .thenComposeAsync(r -> r.after(l -> fetchItemsRelatedRecords(l, repositories)))
      .thenComposeAsync(r -> r.after(l -> fetchFeeFineOwners(l, repositories)))
      .thenApply(this::mapResultToJson)
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<List<LostItemRequiringCostsFee>>> mapToLostItem(MultipleRecords<Loan> loans) {
    return CompletableFuture.completedFuture(Result.succeeded(loans.getRecords().stream()
      .map(loan -> new LostItemRequiringCostsFee(loan))
      .collect(Collectors.toList())));
  }

  private CompletableFuture<Result<List<LostItemRequiringCostsFee>>> fetchItemsRelatedRecords(
    List<LostItemRequiringCostsFee> lostItems, Repositories repositories) {

    return allOf(lostItems, lostItem -> fetchLostItemRelatedRecords(lostItem, repositories));
  }

  private CompletableFuture<Result<LostItemRequiringCostsFee>>  fetchLostItemRelatedRecords(
    LostItemRequiringCostsFee lostItem, Repositories repositories) {

    final Result<Item> item = Result.succeeded(lostItem.getLoan().getItem());

    return repositories.getItem().fetchItemRelatedRecords(item)
      .thenApply(r -> r.map(i -> lostItem.withItem(i)));
  }

  private CompletableFuture<Result<List<LostItemRequiringCostsFee>>> fetchFeeFineOwners(
    List<LostItemRequiringCostsFee> lostItems, Repositories repositories) {

    final Set<String> ownerServicePoints = uniqueSetOf(lostItems,
      LostItemRequiringCostsFee::getOwnerServicePointId);

    return repositories.getFeeFineOwnerRepository().findOwnersForServicePoints(ownerServicePoints)
      .thenApply(r -> r.map(owners -> mapOwnersToLoans(owners, lostItems)));
  }

  private List<LostItemRequiringCostsFee> mapOwnersToLoans(Collection<FeeFineOwner> owners,
    List<LostItemRequiringCostsFee> lostItems) {

    final Map<String, FeeFineOwner> servicePointToOwner = new HashMap<>();

    owners.forEach(owner -> owner.getServicePoints()
      .forEach(servicePoint -> servicePointToOwner.put(servicePoint, owner)));

    return lostItems.stream()
      .map(lostItem -> lostItem.withFeeFineOwners(servicePointToOwner))
      .collect(Collectors.toList());
  }

  private MultipleRecords<Loan> filterOutLoans(MultipleRecords<Loan> multipleLoans) {
    return multipleLoans
      .filter(loan -> hasNoLostFeeFineActualCostsAccounts(loan))
      .filter(loan -> hasNoChargeableSetCostFee(loan))
      .filter(loan -> hasActualCostFee(loan));
  }

  private boolean hasNoLostFeeFineActualCostsAccounts(Loan loan) {
    for (Account account : loan.getAccounts()) {
      if (account.getFeeFineType().equals(FeeFine.LOST_ITEM_FEE_ACTUAL_COSTS_TYPE)) {
        return false;
      }
    }
    return true;
  }

  private boolean hasNoChargeableSetCostFee(Loan loan) {
    if (loan.getLostItemPolicy() != null
      && loan.getLostItemPolicy().getSetCostFee() != null) {
      return !loan.getLostItemPolicy().getSetCostFee().isChargeable();
    }
    return true;
  }

  private boolean hasActualCostFee(Loan loan) {
    if (loan.getLostItemPolicy() != null && loan.getLostItemPolicy().getActualCostFee() != null) {
      return loan.getLostItemPolicy().getActualCostFee().isChargeable();
    }
    return false;
  }

  private Result<JsonObject> mapResultToJson(Result<List<LostItemRequiringCostsFee>> result) {

    return result.map(
      loans -> loans.stream()
        .filter(Objects::nonNull)
        .map(e -> ItemLostRequiringActualCostsRepresentation.mapToResult(e))
        .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add))
    ).next(jsonArray -> Result.succeeded(new JsonObject()
      .put(RECORD_KEY, jsonArray)
      .put(TOTAL_RECORDS_KEY, jsonArray.size()))
    );
  }

  private Result<CqlQuery> buildQuery() {
    final Result<CqlQuery> openLoans = CqlQuery.exactMatch("status.name", LoanStatus.OPEN.getValue());
    final Result<CqlQuery> declaredLost = CqlQuery.exactMatch("itemStatus", DECLARED_LOST.getValue());
    final Result<CqlQuery> agedToLost = CqlQuery.exactMatch("itemStatus", AGED_TO_LOST.getValue());
    final Result<CqlQuery> billed = CqlQuery.exactMatch("agedToLostDelayedBilling.lostItemHasBeenBilled", "true");
    final Result<CqlQuery> billedAgedToLost = agedToLost.combine(billed, CqlQuery::and);
    final Result<CqlQuery> declaredOrAgedToLost = declaredLost.combine(billedAgedToLost, CqlQuery::orGroup);

    return openLoans.combine(declaredOrAgedToLost, CqlQuery::andGroup);
  }

  @AllArgsConstructor
  @Getter
  private class Repositories {
    private final ItemRepository item;
    private final LoanRepository loan;
    private final ServicePointRepository servicePoint;
    private final UserRepository user;
    private final LostItemPolicyRepository lostItemPolicy;
    private final FeeFineOwnerRepository feeFineOwnerRepository;

    public Repositories(Clients clients) {
      this(
        new ItemRepository(clients, true, true, true), new LoanRepository(clients),
        new ServicePointRepository(clients), new UserRepository(clients),
        new LostItemPolicyRepository(clients), new FeeFineOwnerRepository(clients)
      );
    }
  }
}
