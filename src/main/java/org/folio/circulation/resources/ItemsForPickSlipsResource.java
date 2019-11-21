package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemsReportFetcher;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ReportRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.WebContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;

public class ItemsForPickSlipsResource extends Resource {

  private static final int FAKE_LIMIT = 100;

  private static final String SERVICE_POINT_ID_PARAM = "servicePointId";
  private static final String STATUS_NAME_KEY = "status.name";
  private static final String REQUEST_STATUS_KEY = "status";
  private static final String REQUEST_ITEM_ID_KEY = "itemId";
  private static final String REQUESTS_KEY = "requests";
  private static final String ITEMS_KEY = "items";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";


  private final String rootPath;

  public ItemsForPickSlipsResource(String rootPath, HttpClient client) {
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

    final String servicePointId = routingContext.request().getParam(SERVICE_POINT_ID_PARAM);

    final CollectionResourceClient itemsStorageClient = clients.itemsStorage();
    final CollectionResourceClient requestsStorageClient = clients.requestsStorage();
    final ReportRepository reportRepository = new ReportRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final RequestRepository requestRepository = RequestRepository.using(clients);

    // TODO: filter items by effective location

    reportRepository.getAllItemsByField(STATUS_NAME_KEY, PAGED.getValue())
        .thenComposeAsync(result -> mapFetcherToItemList(result.value()))
        .thenComposeAsync(items -> findItemsWithUnfilledOpenRequests(requestsStorageClient, items))
        .thenApply(this::mapResultToJson)
        .thenApply(OkJsonResponseResult::from)
        .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private CompletableFuture<List<Item>> mapFetcherToItemList(ItemsReportFetcher fetcher) {
    List<Item> items = fetcher.getResultListOfItems().stream()
        .flatMap(result -> result.value().getRecords().stream())
        .filter(item -> StringUtils.isNoneBlank(item.getItemId()))
        .collect(Collectors.toList());

    return CompletableFuture.completedFuture(items);
  }

  private CompletableFuture<List<Item>> findItemsWithUnfilledOpenRequests(
      CollectionResourceClient client,
      List<Item> items) {

    List<String> itemIds = items.stream()
        .map(Item::getItemId)
        .collect(Collectors.toList());

    return findUnfilledOpenRequestsForItems(client, itemIds)
        .thenComposeAsync(requests -> filterRequestedItems(items, requests));

  }

  private CompletableFuture<List<Item>> filterRequestedItems(List<Item> items, List<Request> requests) {
    List<String> requestedItemIds = requests.stream()
        .map(Request::getItemId)
        .collect(Collectors.toList());

    List<Item> requestedItems = items.stream()
        .filter(i -> requestedItemIds.contains(i.getItemId()))
        .collect(Collectors.toList());

    return CompletableFuture.completedFuture(requestedItems);
  }

  private CompletableFuture<List<Request>> findUnfilledOpenRequestsForItems(
      CollectionResourceClient client,
      List<String> itemIds) {

    final Result<CqlQuery> statusQuery = exactMatch(REQUEST_STATUS_KEY, RequestStatus.OPEN_NOT_YET_FILLED.getValue());
    final Result<CqlQuery> itemIdsQuery = exactMatchAny(REQUEST_ITEM_ID_KEY, itemIds);

    CompletableFuture<Result<Response>> queryResultFuture = statusQuery
        .combine(itemIdsQuery, CqlQuery::and)
        .after(query -> client.getMany(query, FAKE_LIMIT));

    return queryResultFuture.thenApply(r -> mapResponseToRequestList(r.value()));
  }

  private List<Request> mapResponseToRequestList(Response response) {
    return new ArrayList<>(
        MultipleRecords.from(response, Request::from, REQUESTS_KEY)
        .value()
        .getRecords());
  }

  private Result<JsonObject> mapResultToJson(List<Item> items) {
    List<JsonObject> jsonItems = items.stream()
        .map(Item::getItem)
        .collect(Collectors.toList());

    JsonObject jsonResult = new JsonObject().put(ITEMS_KEY, jsonItems);
    return Result.succeeded(jsonResult);
  }

}
