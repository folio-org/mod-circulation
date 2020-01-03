package org.folio.circulation.resources;

import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.RequestStatus.CLOSED_CANCELLED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_PICKUP_EXPIRED;
import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.CqlSortBy.descending;
import static org.folio.circulation.support.http.client.Limit.limit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.HoldShelfClearanceRequestContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ReportRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.ItemsReportFetcher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestHoldShelfClearanceResource extends Resource {

  /**
   * The optimal number of identifiers that will not exceed the permissible length
   * of the URI in according to the RFC 2616
   */
  private static final int BATCH_SIZE = 40;

  /**
   * Default limit value on a query
   */
  private static final int PAGE_REQUEST_LIMIT = 1;
  private static final String SERVICE_POINT_ID_PARAM = "servicePointId";
  private static final String ITEM_ID_KEY = "itemId";
  private static final String REQUESTS_KEY = "requests";
  private static final String STATUS_KEY = "status";
  private static final String STATUS_NAME_KEY = "status.name";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String REQUEST_CLOSED_DATE_KEY = "awaitingPickupRequestClosedDate";

  private final String rootPath;

  public RequestHoldShelfClearanceResource(String rootPath, HttpClient client) {
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

    final ItemRepository itemRepository = new ItemRepository(clients, false, false, false);
    final CollectionResourceClient requestsStorage = clients.requestsStorage();
    final ReportRepository reportRepository = new ReportRepository(clients);

    final String servicePointId = routingContext.request().getParam(SERVICE_POINT_ID_PARAM);

    reportRepository.getAllItemsByField(STATUS_NAME_KEY, AWAITING_PICKUP.getValue())
      .thenComposeAsync(r -> r.after(this::mapContextToItemIdList))
      .thenComposeAsync(r -> r.after(this::mapItemIdsInBatchItemIds))
      .thenComposeAsync(r -> findAwaitingPickupRequestsByItemsIds(requestsStorage, r.value()))
      .thenComposeAsync(r -> findExpiredOrCancelledRequestByItemIds(requestsStorage, r.value()))
      .thenApply(r -> findExpiredOrCancelledRequestByServicePoint(servicePointId, r.value()))
      .thenApply(r -> fetchItemToRequest(r, itemRepository))
      .thenApply(this::mapResultToJson)
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<List<String>>> mapContextToItemIdList(ItemsReportFetcher itemsReportFetcher) {
    List<String> itemIds = itemsReportFetcher.getResultListOfItems().stream()
      .flatMap(records -> records.value().getRecords().stream())
      .filter(item -> StringUtils.isNoneBlank(item.getItemId()))
      .map(Item::getItemId)
      .collect(Collectors.toList());
    return CompletableFuture.completedFuture(Result.succeeded(itemIds));
  }

  private CompletableFuture<Result<List<List<String>>>> mapItemIdsInBatchItemIds(List<String> itemIds) {
    return CompletableFuture.completedFuture(Result.succeeded(splitIds(itemIds)));
  }

  private List<List<String>> splitIds(List<String> itemsIds) {
    int size = itemsIds.size();
    if (size <= 0) {
      return new ArrayList<>();
    }

    int fullChunks = (size - 1) / BATCH_SIZE;
    return IntStream.range(0, fullChunks + 1)
      .mapToObj(n ->
        itemsIds.subList(n * BATCH_SIZE, n == fullChunks
          ? size
          : (n + 1) * BATCH_SIZE))
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<HoldShelfClearanceRequestContext>> findAwaitingPickupRequestsByItemsIds(CollectionResourceClient client,
                                                                                                           List<List<String>> batchItemIds) {
    List<Result<MultipleRecords<Request>>> awaitingPickupRequests = findAwaitingPickupRequests(client, batchItemIds);
    return CompletableFuture.completedFuture(Result.succeeded(
      createHoldShelfClearanceRequestContext(batchItemIds, awaitingPickupRequests)));
  }

  private List<Result<MultipleRecords<Request>>> findAwaitingPickupRequests(CollectionResourceClient client,
                                                                            List<List<String>> batchItemIds) {
    return batchItemIds.stream()
      .map(batch -> {
        final Result<CqlQuery> statusQuery = exactMatch(STATUS_KEY, OPEN_AWAITING_PICKUP.getValue());
        final Result<CqlQuery> itemIdsQuery = exactMatchAny(ITEM_ID_KEY, batch);

        Result<CqlQuery> cqlQueryResult = statusQuery
          .combine(itemIdsQuery, CqlQuery::and);

        return findRequestsByCqlQuery(client, cqlQueryResult, batch.size());
      })
      .map(CompletableFuture::join)
      .collect(Collectors.toList());
  }

  private HoldShelfClearanceRequestContext createHoldShelfClearanceRequestContext(List<List<String>> batchItemIds,
                                                                                  List<Result<MultipleRecords<Request>>> results) {
    List<String> allAwaitingPickupItemIds = batchItemIds.stream()
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    List<String> awaitingPickupRequestItemIds = results.stream()
      .flatMap(r -> r.value().getRecords().stream())
      .map(Request::getItemId)
      .collect(Collectors.toList());

    allAwaitingPickupItemIds.removeAll(awaitingPickupRequestItemIds);

    return new HoldShelfClearanceRequestContext()
      .withAwaitingPickupItemIds(allAwaitingPickupItemIds)
      .withAwaitingPickupRequestItemIds(awaitingPickupRequestItemIds);
  }

  private CompletableFuture<Result<HoldShelfClearanceRequestContext>> findExpiredOrCancelledRequestByItemIds(CollectionResourceClient client,
                                                                                                             HoldShelfClearanceRequestContext context) {
    List<Result<MultipleRecords<Request>>> requestList = findRequestsSortedByClosedDate(client, context.getAwaitingPickupItemIds());
    List<Request> firstRequestFromList = getFirstRequestFromList(requestList);
    return CompletableFuture.completedFuture(Result.succeeded(context.withExpiredOrCancelledRequests(firstRequestFromList)));
  }

  private Predicate<Request> hasContextRequestForServicePoint(String servicePointId) {
    return r -> r.getPickupServicePointId().equals(servicePointId);
  }

  private Predicate<Request> hasNotContextAwaitingPickupRequestForItemId(HoldShelfClearanceRequestContext context) {
    return req -> !context.getAwaitingPickupRequestItemIds().contains(req.getItemId());
  }

  private Result<List<Request>> findExpiredOrCancelledRequestByServicePoint(String servicePointId,
                                                                            HoldShelfClearanceRequestContext context) {
    List<Request> requestList = context.getExpiredOrCancelledRequests().stream()
      .filter(hasContextRequestForServicePoint(servicePointId))
      .filter(hasNotContextAwaitingPickupRequestForItemId(context))
      .collect(Collectors.toList());
    return Result.succeeded(requestList);
  }

  /**
   * Find for each item ids requests sorted by awaitingPickupRequestClosedDate
   */
  private List<Result<MultipleRecords<Request>>> findRequestsSortedByClosedDate(CollectionResourceClient client,
                                                                                List<String> itemIds) {
    return itemIds.stream()
      .filter(Objects::nonNull)
      .map(itemId -> {
        final Result<CqlQuery> itemIdQuery = CqlQuery.exactMatch(ITEM_ID_KEY, itemId);
        final Result<CqlQuery> notEmptyDateQuery = CqlQuery.greaterThan(REQUEST_CLOSED_DATE_KEY, StringUtils.EMPTY);
        final Result<CqlQuery> statusQuery = exactMatchAny(STATUS_KEY,
          Arrays.asList(CLOSED_PICKUP_EXPIRED.getValue(), CLOSED_CANCELLED.getValue()));

        Result<CqlQuery> cqlQueryResult = itemIdQuery
          .combine(statusQuery, CqlQuery::and)
          .combine(notEmptyDateQuery, CqlQuery::and)
          .map(q -> q.sortBy(descending(REQUEST_CLOSED_DATE_KEY)));

        return findRequestsByCqlQuery(client, cqlQueryResult, PAGE_REQUEST_LIMIT);
      }).map(CompletableFuture::join)
      .collect(Collectors.toList());
  }

  private List<Request> getFirstRequestFromList(List<Result<MultipleRecords<Request>>> multipleRecordsList) {
    return multipleRecordsList.stream()
      .map(r -> r.value().getRecords().stream().findFirst())
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<MultipleRecords<Request>>> findRequestsByCqlQuery(
    CollectionResourceClient client, Result<CqlQuery> cqlQueryResult, int limit) {

    return cqlQueryResult
      .after(query -> client.getMany(query, limit(limit)))
      .thenApply(result -> result.next(this::mapResponseToRequest));
  }

  private Result<List<Result<Request>>> fetchItemToRequest(Result<List<Request>> requests,
    ItemRepository itemRepository) {

    return requests.map(r -> r.stream()
      .map(request -> fetchItem(itemRepository, request))
      .map(CompletableFuture::join)
      .collect(Collectors.toList()));
  }

  private Result<JsonObject> mapResultToJson(Result<List<Result<Request>>> requests) {
    return requests
      .map(resultList ->
        Result.combineAll(resultList).map(r -> r.stream()
          .map(result -> new RequestRepresentation().extendedRepresentation(result))
          .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add))))
      .next(r -> r.next(jsonArray -> Result.succeeded(new JsonObject()
        .put(REQUESTS_KEY, jsonArray)
        .put(TOTAL_RECORDS_KEY, jsonArray.size()))));
  }

  private CompletableFuture<Result<Request>> fetchItem(ItemRepository itemRepository, Request request) {
    return CompletableFuture.completedFuture(Result.succeeded(request))
      .thenComposeAsync(result -> result.combineAfter(itemRepository::fetchFor, Request::withItem));
  }

  private Result<MultipleRecords<Request>> mapResponseToRequest(Response response) {
    return MultipleRecords.from(response, Request::from, REQUESTS_KEY);
  }
}
