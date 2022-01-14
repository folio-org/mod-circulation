package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.support.results.Result.combineAll;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.User;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemReportRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.support.ItemsInTransitReportContext;
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

  public CompletableFuture<Result<JsonObject>> buildReport() {
    return completedFuture(succeeded(new ItemsInTransitReportContext()))
      .thenCompose(r -> r.after(this::fetchItems))
      .thenCompose(this::fetchHoldingsRecords)
      .thenCompose(r -> r.after(this::fetchInstances))
      .thenCompose(this::fetchLocations)
      .thenCompose(this::fetchMaterialTypes)
      .thenCompose(this::fetchLoanTypes)
      .thenCompose(this::fetchLoans)
      .thenCompose(this::fetchRequests)
      .thenCompose(r -> r.after(this::fetchUsers))
      .thenCompose(this::fetchPatronGroups)
      .thenCompose(this::fetchServicePoints)
      .thenApply(this::mapToJsonObject);
  }

  private Result<JsonObject> mapToJsonObject(Result<ItemsInTransitReportContext> context) {

    return succeeded(new JsonObject());
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchItems(
    ItemsInTransitReportContext context) {

    return itemReportRepository.getAllItemsByField("status.name", IN_TRANSIT.getValue())
      .thenApply(r -> r.next(itemsReportFetcher ->
        combineAll(itemsReportFetcher.getResultListOfItems())
          .map(listOfPages -> listOfPages.stream()
            .flatMap(page -> page.getRecords().stream())
            .collect(Collectors.toList()))))
      .thenApply(r -> r.map(items -> toMap(items, Item::getItemId)))
      .thenApply(r -> r.map(context::withItems));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchHoldingsRecords(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchInstances(
    ItemsInTransitReportContext context) {

    return succeeded(mapToStrings(context.getItems().values(), Item::getInstanceId))
      .after(itemRepository::findInstancesByIds)
      .thenApply(r -> r.map(records -> toMap(records.getRecords(), Instance::getId)))
      .thenApply(r -> r.map(context::withInstances));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLocations(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchMaterialTypes(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLoanTypes(
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
    ItemsInTransitReportContext context) {

    return userRepository.findUsersByRequests(context.getRequests().values())
        .thenApply(r -> r.map(userMultipleRecords -> toMap(userMultipleRecords.getRecords(), User::getId)))
        .thenApply(r -> r.map(context::withUsers));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchPatronGroups(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  // Needs to fetch all service points for items, loans and requests
  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchServicePoints(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private <T> Set<String> mapToStrings(Collection<T> collection, Function<T, String> mapper) {
    return collection.stream()
      .map(mapper)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  public <T> Map<String, T> toMap(Collection<T> collection, Function<T, String> idMapper) {
    return collection.stream()
      .collect(Collectors.toMap(idMapper, identity()));
  }
}
