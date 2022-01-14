package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.support.results.Result.combineAll;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.PatronGroup;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.representations.ItemInTransitReport;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemReportRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.support.ItemsInTransitReportContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

/**
 * Placeholder class which will replace items-in-transit report logic when implemented
 */
@AllArgsConstructor
public class ItemsInTransitReportService {
  private ItemReportRepository itemReportRepository;
  private GetManyRecordsClient loansStorageClient;
  private ServicePointRepository servicePointRepository;
  private GetManyRecordsClient requestsStorageClient;
  private ItemRepository itemRepository;
  private UserRepository userRepository;
  private PatronGroupRepository patronGroupRepository;

  public ItemsInTransitReportService(Clients clients) {
    this.loansStorageClient = clients.loansStorage();
    this.requestsStorageClient = clients.requestsStorage();
    this.itemRepository = new ItemRepository(clients, true, true, true);
    this.servicePointRepository = new ServicePointRepository(clients);
    this.itemReportRepository = new ItemReportRepository(clients);
    this.userRepository = new UserRepository(clients);
    this.patronGroupRepository = new PatronGroupRepository(clients);
  }

  public CompletableFuture<Result<JsonObject>> buildReport() {
    return completedFuture(succeeded(new ItemsInTransitReportContext()))
      .thenCompose(r -> r.after(this::fetchItems))
      .thenCompose(this::fetchHoldingsRecords)
      .thenCompose(this::fetchInstances)
      .thenCompose(this::fetchLocations)
      .thenCompose(this::fetchLoans)
      .thenCompose(this::fetchRequests)
      .thenCompose(this::fetchUsers)
      .thenCompose(r -> r.after(this::fetchPatronGroups))
      .thenCompose(this::fetchServicePoints)
      .thenApply(this::mapToJsonObject);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchItems(
    ItemsInTransitReportContext context) {

    return itemReportRepository.getAllItemsByField("status.name", IN_TRANSIT.getValue())
      .thenApply(r -> r.next(itemsReportFetcher ->
        combineAll(itemsReportFetcher.getResultListOfItems())
          .map(listOfPages -> listOfPages.stream()
            .flatMap(page -> page.getRecords().stream())
            .collect(toList()))))
      .thenApply(r -> r.map(items -> toMap(items, Item::getItemId)))
      .thenApply(r -> r.map(context::withItems));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchHoldingsRecords(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchInstances(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLocations(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLoans(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchRequests(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchUsers(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchPatronGroups(
    ItemsInTransitReportContext context) {

    List<String> patronGroupIds = context.getUsers()
      .values()
      .stream()
      .map(User::getPatronGroupId)
      .collect(toList());

    return ofAsync(() -> patronGroupIds)
      .thenCompose(r -> r.after(patronGroupRepository::findPatronGroupsByIds))
      .thenApply(r -> r.map(groups -> toMap(groups, PatronGroup::getId)))
      .thenApply(r -> r.map(context::withPatronGroups));
  }

  // Needs to fetch all service points for items, loans and requests
  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchServicePoints(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  public <T> Map<String, T> toMap(Collection<T> list, Function<T, String> idMapper) {
    return list.stream()
      .collect(Collectors.toMap(idMapper, identity()));
  }

  private Result<JsonObject> mapToJsonObject(Result<ItemsInTransitReportContext> context) {
    return context.map(ItemInTransitReport::new)
      .map(ItemInTransitReport::build);
  }
}
