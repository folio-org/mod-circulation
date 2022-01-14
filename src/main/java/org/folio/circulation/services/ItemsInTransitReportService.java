package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.support.results.Result.combineAll;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemReportRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
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
  private LoanRepository loanRepository;
  private LocationRepository locationRepository;
  private ServicePointRepository servicePointRepository;
  private GetManyRecordsClient requestsStorageClient;
  private ItemRepository itemRepository;
  private UserRepository userRepository;
  private PatronGroupRepository patronGroupRepository;

  public CompletableFuture<Result<JsonObject>> buildReport() {
    return completedFuture(succeeded(new ItemsInTransitReportContext()))
      .thenCompose(r -> r.after(this::fetchItems))
      .thenCompose(r -> r.after(this::fetchHoldingsRecords))
      .thenCompose(this::fetchInstances)
      .thenCompose(r -> r.after(this::fetchLocations))
      .thenCompose(r -> r.after(this::fetchLoans))
      .thenCompose(this::fetchRequests)
      .thenCompose(this::fetchUsers)
      .thenCompose(this::fetchPatronGroups)
      .thenCompose(r -> r.after(this::fetchServicePoints))
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
    ItemsInTransitReportContext context) {

    return succeeded(mapToStrings(context.getItems().values(), Item::getHoldingsRecordId))
      .after(itemRepository::findHoldingsByIds)
      .thenApply(r -> r.map(records -> toMap(records.getRecords(), Holdings::getId)))
      .thenApply(r -> r.map(context::withHoldingsRecords));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchInstances(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLocations(
    ItemsInTransitReportContext context) {

    return locationRepository
      .getItemLocations(context.getItems().values(), List.of(Item::getLocationId))
      .thenApply(r -> r.map(context::withLocations));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLoans(
    ItemsInTransitReportContext context) {

    return succeeded(findIds(context.getItems().values(), Item::getItemId))
      .after(itemIds -> loanRepository.findByItemIds(itemIds.collect(Collectors.toSet())))
      .thenApply(mapResult(this::toList))
      .thenApply(mapResult(loans -> toMap(loans, Loan::getId)))
      .thenApply(mapResult(context::withLoans));
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
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchServicePoints(
    ItemsInTransitReportContext context) {

    Collection<Item> items = context.getItems().values();
    Stream<String> itemInTransitDestinationServicePointIds = items.stream()
      .map(Item::getInTransitDestinationServicePointId)
      .filter(Objects::nonNull);
    Stream<String> itemLastCheckInServicePointIds = items.stream()
      .map(Item::getLastCheckInServicePointId)
      .filter(Objects::nonNull)
      .map(UUID::toString);

    Collection<Loan> loans = context.getLoans().values();
    Stream<String> loanCheckInServicePointIds = loans.stream()
      .map(Loan::getCheckInServicePointId)
      .filter(Objects::nonNull);
    Stream<String> loanCheckoutServicePointIds = loans.stream()
      .map(Loan::getCheckoutServicePointId)
      .filter(Objects::nonNull);

    Stream<String> requestServicePointIds = context.getRequests().values().stream()
      .map(Request::getPickupServicePointId)
      .filter(Objects::nonNull);

    Set<String> servicePointIds = Stream.of(itemInTransitDestinationServicePointIds,
        itemLastCheckInServicePointIds, loanCheckInServicePointIds, loanCheckoutServicePointIds,
        requestServicePointIds)
      .flatMap(identity())
      .filter(Objects::nonNull)
      .collect(toSet());

    return succeeded(servicePointIds)
      .after(servicePointRepository::findServicePointsByIds)
      .thenApply(mapResult(this::toList))
      .thenApply(mapResult(servicePoints -> toMap(servicePoints, ServicePoint::getId)))
      .thenApply(mapResult(context::withServicePoints));
  }

  private <T> Stream<String> findIds(Collection<T> entities,
    Function<T, String> getIdFunction) {

    return entities.stream()
      .filter(Objects::nonNull)
      .map(getIdFunction)
      .filter(Objects::nonNull);
  }

  private <T> List<T> toList(MultipleRecords<T> records) {
    return new ArrayList<>(records.getRecords());
  }

  private <T> Set<String> mapToStrings(Collection<T> collection, Function<T, String> mapper) {
    return collection.stream()
    .map(mapper)
    .filter(StringUtils::isNotBlank)
    .collect(Collectors.toSet());
  }

  public <T> Map<String, T> toMap(Collection<T> collection, Function<T, String> keyMapper) {
    return collection.stream()
      .collect(Collectors.toMap(keyMapper, identity()));
  }
}
