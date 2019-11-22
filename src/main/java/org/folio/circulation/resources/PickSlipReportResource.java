package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemsReportFetcher;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ReportRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.WebContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;

public class PickSlipReportResource extends Resource {

  private static final int FAKE_LIMIT = 100;

  private static final String SERVICE_POINT_ID_PARAM = "servicePointId";
  private static final String STATUS_NAME_KEY = "status.name";
  private static final String REQUEST_STATUS_KEY = "status";
  private static final String REQUEST_ITEM_ID_KEY = "itemId";
  private static final String REQUESTS_KEY = "requests";
  private static final String ITEMS_KEY = "items";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";


  private final String rootPath;

  public PickSlipReportResource(String rootPath, HttpClient client) {
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

    final UUID servicePointId = UUID.fromString(
        routingContext.request().getParam(SERVICE_POINT_ID_PARAM));

    final CollectionResourceClient requestsStorageClient = clients.requestsStorage();
    final ReportRepository reportRepository = new ReportRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, false, false);

    reportRepository.getAllItemsByField(STATUS_NAME_KEY, PAGED.getValue())
        .thenComposeAsync(r -> r.after(fetcher -> fetchItemRelatedRecords(fetcher, itemRepository)))
        .thenComposeAsync(r -> r.after(items -> filterItemsByServicePoint(items, servicePointId)))
        .thenComposeAsync(r -> r.after(items -> findItemsWithUnfilledRequests(items, requestsStorageClient)))
        .thenApply(r -> r.next(this::mapResultToJson))
        .thenApply(OkJsonResponseResult::from)
        .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<List<Item>>> fetchItemRelatedRecords(ItemsReportFetcher fetcher,
      ItemRepository repository) {

    List<Item> items = fetcher.getResultListOfItems().stream()
        .flatMap(resultListOfItem -> resultListOfItem.value().getRecords().stream())
        .collect(Collectors.toList());

    return allOf(items, item -> fetchItemRelatedRecords(repository, item));
  }

  private CompletableFuture<Result<Item>> fetchItemRelatedRecords(ItemRepository repository, Item item) {
    return CompletableFuture.completedFuture(Result.succeeded(item))
        .thenComposeAsync(repository::fetchItemRelatedRecords);
  }

  private CompletableFuture<Result<List<Item>>> filterItemsByServicePoint(List<Item> items, UUID servicePointId) {
    List<Item> result = items.stream()
        .filter(item -> servicePointId.equals(item.getLocation().getPrimaryServicePointId()))
        .collect(Collectors.toList());

    return CompletableFuture.completedFuture(Result.succeeded(result));
  }

  private CompletableFuture<Result<List<Item>>> findItemsWithUnfilledRequests(
      List<Item> items, CollectionResourceClient client) {

    List<String> itemIds = items.stream()
        .map(Item::getItemId)
        .collect(Collectors.toList());

    return fetchUnfilledOpenRequestsForItems(client, itemIds)
        .thenApply(r -> r.next(this::mapResponseToRecords))
        .thenApply(r -> r.next(records -> filterRequestedItems(items, records)));
  }

  private CompletableFuture<Result<Response>> fetchUnfilledOpenRequestsForItems(
      CollectionResourceClient client,
      List<String> itemIds) {

    final Result<CqlQuery> statusQuery = exactMatch(REQUEST_STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue());
    final Result<CqlQuery> itemIdsQuery = exactMatchAny(REQUEST_ITEM_ID_KEY, itemIds);

    return statusQuery
        .combine(itemIdsQuery, CqlQuery::and)
        .after(query -> client.getMany(query, FAKE_LIMIT));
  }

  private Result<MultipleRecords<Request>> mapResponseToRecords(Response response) {
    return MultipleRecords.from(response, Request::from, REQUESTS_KEY);
  }

  private Result<List<Item>> filterRequestedItems(List<Item> items, MultipleRecords<Request> records) {
    List<String> requestedItemIds = records
        .toKeys(Function.identity())
        .stream()
        .map(Request::getItemId)
        .collect(Collectors.toList());

    List<Item> requestedItems = items.stream()
        .filter(i -> requestedItemIds.contains(i.getItemId()))
        .collect(Collectors.toList());

    return Result.succeeded(requestedItems);
  }

  private Result<JsonObject> mapResultToJson(List<Item> items) {
    List<JsonObject> jsonItems = items.stream()
        .map(item -> new ItemSummaryRepresentation().createItemSummary(item))
        .collect(Collectors.toList());

    JsonObject jsonResult = new JsonObject()
        .put(ITEMS_KEY, jsonItems)
        .put(TOTAL_RECORDS_KEY, jsonItems.size());

    return Result.succeeded(jsonResult);
  }

}