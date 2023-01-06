package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.support.results.Result.combineAll;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.PatronGroup;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.representations.ItemsInTransitReport;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.InstanceRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemReportRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.support.ItemsInTransitReportContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ItemsInTransitReportService {
  private ItemReportRepository itemReportRepository;
  private LoanRepository loanRepository;
  private LocationRepository locationRepository;
  private ServicePointRepository servicePointRepository;
  private RequestRepository requestRepository;
  private ItemRepository itemRepository;
  private UserRepository userRepository;
  private PatronGroupRepository patronGroupRepository;
  private final InstanceRepository instanceRepository;
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public ItemsInTransitReportService(Clients clients) {
    this.itemReportRepository = new ItemReportRepository(clients);
    this.itemRepository = new ItemRepository(clients);
    this.userRepository = new UserRepository(clients);
    this.loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    this.servicePointRepository = new ServicePointRepository(clients);
    this.locationRepository = LocationRepository.using(clients, servicePointRepository);
    this.requestRepository = new RequestRepository(clients, itemRepository, userRepository,
      loanRepository, servicePointRepository, patronGroupRepository);
    this.patronGroupRepository = new PatronGroupRepository(clients);
    this.instanceRepository = new InstanceRepository(clients);
  }

  public CompletableFuture<Result<JsonObject>> buildReport() {
    return completedFuture(succeeded(new ItemsInTransitReportContext()))
      .thenCompose(r -> r.after(this::fetchItems))
      .thenCompose(r -> r.after(this::fetchHoldingsRecords))
      .thenCompose(r -> r.after(this::fetchInstances))
      .thenCompose(r -> r.after(this::fetchLocations))
      .thenCompose(r -> r.after(this::fetchLoans))
      .thenCompose(r -> r.after(this::fetchRequests))
      .thenCompose(r -> r.after(this::fetchUsers))
      .thenCompose(r -> r.after(this::fetchPatronGroups))
      .thenCompose(r -> r.after(this::fetchServicePoints))
      .thenApply(this::mapToJsonObject)
      .whenComplete(this::handleResult);
  }

  private void handleResult(Result<JsonObject> result, Throwable throwable) {
    if (throwable != null) {
      log.error("An exception was caught while building the report", throwable);
    } else if (result != null) {
      if (result.failed()) {
        log.error("Report construction failed: {}", result.cause());
      } else {
        log.info("Report built successfully");
      }
    } else {
      log.error("Result and throwable are null");
    }
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchItems(
    ItemsInTransitReportContext context) {

    return itemReportRepository.getAllItemsByField("status.name", IN_TRANSIT.getValue())
      .thenApply(r -> r.next(itemsReportFetcher ->
        combineAll(itemsReportFetcher.getResultListOfItems())
          .map(listOfPages -> listOfPages.stream()
            .flatMap(page -> page.getRecords().stream())
            .collect(Collectors.toList()))))
      .thenApply(mapResult(items -> toMap(items, Item::getItemId)))
      .thenApply(mapResult(context::withItems));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchHoldingsRecords(
    ItemsInTransitReportContext context) {

    return succeeded(mapToStrings(context.getItems().values(), Item::getHoldingsRecordId))
      .after(itemRepository::findHoldingsByIds)
      .thenApply(mapResult(records -> toMap(records.getRecords(), Holdings::getId)))
      .thenApply(mapResult(context::withHoldingsRecords));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchInstances(
    ItemsInTransitReportContext context) {

    return instanceRepository.fetchByIds(mapToStrings(context.getItems().values(),
          context::getInstanceId))
      .thenApply(mapResult(records -> toMap(records.getRecords(), Instance::getId)))
      .thenApply(mapResult(context::withInstances));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLocations(
    ItemsInTransitReportContext context) {

    return locationRepository
      .getItemLocations(context.getItems().values())
      .thenApply(mapResult(context::withLocations));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLoans(
    ItemsInTransitReportContext context) {

    return succeeded(context.getItems().keySet())
      .after(loanRepository::findByItemIds)
      .thenApply(mapResult(loans -> toMap(loans, Loan::getItemId)))
      .thenApply(mapResult(context::withLoans));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchRequests(
    ItemsInTransitReportContext context) {

    return requestRepository.findOpenRequestsByItemIds(context.getItems().keySet())
      .thenApply(mapResult(requests -> toMap(requests.getRecords(), Request::getItemId)))
      .thenApply(mapResult(context::withRequests));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchUsers(
    ItemsInTransitReportContext context) {

    return userRepository.findUsersByRequests(context.getRequests().values())
      .thenApply(mapResult(userMultipleRecords -> toMap(userMultipleRecords.getRecords(),
        User::getId)))
      .thenApply(mapResult(context::withUsers));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchPatronGroups(
    ItemsInTransitReportContext context) {

    return ofAsync(() -> mapToStrings(context.getUsers().values(), User::getPatronGroupId))
      .thenCompose(r -> r.after(patronGroupRepository::findPatronGroupsByIds))
      .thenApply(r -> r.map(groups -> toMap(groups, PatronGroup::getId)))
      .thenApply(r -> r.map(context::withPatronGroups));
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchServicePoints(
    ItemsInTransitReportContext context) {

    Collection<Item> items = context.getItems().values();
    Stream<String> itemInTransitDestinationServicePointIds = items.stream()
      .map(Item::getInTransitDestinationServicePointId);
    Stream<String> itemLastCheckInServicePointIds = items.stream()
      .map(Item::getLastCheckInServicePointId)
      .filter(Objects::nonNull)
      .map(UUID::toString);

    Collection<Loan> loans = context.getLoans().values();
    Stream<String> loanCheckInServicePointIds = loans.stream()
      .map(Loan::getCheckInServicePointId);
    Stream<String> loanCheckoutServicePointIds = loans.stream()
      .map(Loan::getCheckoutServicePointId);

    Stream<String> requestServicePointIds = context.getRequests().values().stream()
      .map(Request::getPickupServicePointId);

    Set<String> servicePointIds = Stream.of(itemInTransitDestinationServicePointIds,
        itemLastCheckInServicePointIds, loanCheckInServicePointIds, loanCheckoutServicePointIds,
        requestServicePointIds)
      .flatMap(identity())
      .filter(Objects::nonNull)
      .collect(toSet());

    return succeeded(servicePointIds)
      .after(servicePointRepository::findServicePointsByIds)
      .thenApply(mapResult(servicePoints -> toMap(servicePoints, ServicePoint::getId)))
      .thenApply(mapResult(context::withServicePoints));
  }

  private <T> Set<String> mapToStrings(Collection<T> collection, Function<T, String> mapper) {
    return collection.stream()
    .map(mapper)
    .filter(StringUtils::isNotBlank)
    .collect(Collectors.toSet());
  }

  /**
   * Chooses first value when key mapper generates equal keys
   */
  public <T> Map<String, T> toMap(Collection<T> collection, Function<T, String> keyMapper) {
    return collection.stream()
      .collect(Collectors.toMap(keyMapper, identity(), (left, right) -> left));
  }

  private Result<JsonObject> mapToJsonObject(Result<ItemsInTransitReportContext> context) {
    return context.map(ItemsInTransitReport::new)
      .map(ItemsInTransitReport::build);
  }
}
