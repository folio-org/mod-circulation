package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.RequestStatus.openStates;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Request;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemReportRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.support.ItemsInTransitReportContext;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.CqlQuery;
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
      .thenCompose(this::fetchItems)
      .thenCompose(this::fetchHoldingsRecords)
      .thenCompose(this::fetchInstances)
      .thenCompose(this::fetchLocations)
      .thenCompose(this::fetchMaterialTypes)
      .thenCompose(this::fetchLoanTypes)
      .thenCompose(this::fetchLoans)
      .thenCompose(this::fetchRequests)
      .thenCompose(this::fetchUsers)
      .thenCompose(this::fetchPatronGroups)
      .thenCompose(this::fetchServicePoints)
      .thenApply(this::mapToJsonObject);
  }

  private Result<JsonObject> mapToJsonObject(Result<ItemsInTransitReportContext> context) {

    return succeeded(new JsonObject());
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchItems(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
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

    final Result<CqlQuery> cqlQuery = exactMatchAny("status", openStates());
    FindWithMultipleCqlIndexValues<Request> fetcher
      = findWithMultipleCqlIndexValues(requestsStorageClient, "requests",
        Request::from);

    ItemsInTransitReportContext reportContext = context.value();
    return fetcher.findByIdIndexAndQuery(reportContext.getItems().keySet(), "itemId", cqlQuery)
      .thenApply(requests -> {
        reportContext.setRequests(listToMap(requests.value().getRecords()));
        return context;
      });
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchUsers(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
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

  private Map<String, Request> listToMap(Collection<Request> records) {
    return records.stream()
      .collect(Collectors.toMap(Request::getId, Function.identity()));
  }
}
