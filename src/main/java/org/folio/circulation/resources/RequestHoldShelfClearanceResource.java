package org.folio.circulation.resources;

import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.RequestStatus.CLOSED_CANCELLED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_PICKUP_EXPIRED;
import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.CqlSortBy.descending;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.utils.LogUtil.listAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.HoldShelfClearanceRequestContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemsReportFetcher;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.infrastructure.storage.inventory.ItemReportRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

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
   * Temporary solution caused by batch processing of items (fetching requests for a batch
   * of 40 items, though we only need first one for each item). Even this limit can be exceeded.
   * To avoid this issue the whole report should be moved to mod-circulation-storage which can run
   * DB queries directly.
   */
  private static final int EXPIRED_CANCELLED_REQUEST_LIMIT = 10_000;
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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

    final ItemRepository itemRepository = new ItemRepository(clients);
    final GetManyRecordsClient requestsStorage = clients.requestsStorage();
    final ItemReportRepository itemReportRepository = new ItemReportRepository(clients);

    final String servicePointId = routingContext.request().getParam(SERVICE_POINT_ID_PARAM);

    itemReportRepository.getAllItemsByField(STATUS_NAME_KEY, AWAITING_PICKUP.getValue())
      .thenComposeAsync(r -> r.after(this::mapContextToItemIdList))
      .thenComposeAsync(r -> r.after(this::mapItemIdsInBatchItemIds))
      .thenComposeAsync(r -> findAwaitingPickupRequestsByItemsIds(requestsStorage, r.value()))
      .thenComposeAsync(r -> findExpiredOrCancelledRequestByItemIds(requestsStorage, r.value()))
      .thenApply(r -> findExpiredOrCancelledRequestByServicePoint(servicePointId, r.value()))
      .thenCompose(r -> fetchItemToRequest(r, itemRepository))
      .thenApply(this::mapResultToJson)
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
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

  private CompletableFuture<Result<HoldShelfClearanceRequestContext>> findAwaitingPickupRequestsByItemsIds(
    GetManyRecordsClient client, List<List<String>> batchItemIds) {

    log.debug("findAwaitingPickupRequestsByItemsIds:: parameters batchItemIds: {}",
      () -> listAsString(batchItemIds));
    List<Result<MultipleRecords<Request>>> awaitingPickupRequests = findAwaitingPickupRequests(client, batchItemIds);

    return CompletableFuture.completedFuture(Result.succeeded(
      createHoldShelfClearanceRequestContext(batchItemIds, awaitingPickupRequests)));
  }

  private List<Result<MultipleRecords<Request>>> findAwaitingPickupRequests(
    GetManyRecordsClient client, List<List<String>> batchItemIds) {

    return batchItemIds.stream()
      .map(batch -> {
        final Result<CqlQuery> statusQuery = exactMatch(STATUS_KEY, OPEN_AWAITING_PICKUP.getValue());
        final Result<CqlQuery> itemIdsQuery = exactMatchAny(ITEM_ID_KEY, batch);

        Result<CqlQuery> cqlQueryResult = statusQuery
          .combine(itemIdsQuery, CqlQuery::and);

        return findRequestsByCqlQuery(client, cqlQueryResult, limit(batch.size()));
      })
      .map(CompletableFuture::join)
      .collect(Collectors.toList());
  }

  private HoldShelfClearanceRequestContext createHoldShelfClearanceRequestContext(
    List<List<String>> batchItemIds, List<Result<MultipleRecords<Request>>> results) {

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

  private CompletableFuture<Result<HoldShelfClearanceRequestContext>>
  findExpiredOrCancelledRequestByItemIds(GetManyRecordsClient client,
    HoldShelfClearanceRequestContext context) {

    List<Result<List<Request>>> requestList = findRequestsSortedByClosedDate(client,
      context.getAwaitingPickupItemIds());
    List<Request> firstRequestFromList = getFirstRequestFromList(requestList);
    log.debug("findExpiredOrCancelledRequestByItemIds:: firstRequestFromList: {}",
      () -> listAsString(firstRequestFromList));

    return CompletableFuture.completedFuture(Result.succeeded(
      context.withExpiredOrCancelledRequests(firstRequestFromList)));
  }

  private Predicate<Request> hasContextRequestForServicePoint(String servicePointId) {
    return r -> r.getPickupServicePointId().equals(servicePointId);
  }

  private Predicate<Request> hasNotContextAwaitingPickupRequestForItemId(
    HoldShelfClearanceRequestContext context) {

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
   * Finds requests sorted by awaitingPickupRequestClosedDate. Splits items into batches, only
   * first request from each batch is included in the result (should be enough because we only need
   * first request later)
   */
  private List<Result<List<Request>>> findRequestsSortedByClosedDate(
    GetManyRecordsClient client, List<String> itemIds) {

    return splitIds(itemIds)
      .stream()
      .map(batch -> findRequestsSortedByClosedDateForSingleBatch(client, batch))
      .map(CompletableFuture::join)
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<List<Request>>> findRequestsSortedByClosedDateForSingleBatch(
    GetManyRecordsClient client, List<String> itemIds) {

    final Result<CqlQuery> itemIdQuery = exactMatchAny(ITEM_ID_KEY, itemIds);
    final Result<CqlQuery> notEmptyDateQuery = CqlQuery.greaterThan(REQUEST_CLOSED_DATE_KEY, StringUtils.EMPTY);
    final Result<CqlQuery> statusQuery = exactMatchAny(STATUS_KEY,
      Arrays.asList(CLOSED_PICKUP_EXPIRED.getValue(), CLOSED_CANCELLED.getValue()));

    Result<CqlQuery> cqlQueryResult = itemIdQuery
      .combine(statusQuery, CqlQuery::and)
      .combine(notEmptyDateQuery, CqlQuery::and)
      .map(q -> q.sortBy(descending(REQUEST_CLOSED_DATE_KEY)));

    return findRequestsByCqlQuery(client, cqlQueryResult, limit(EXPIRED_CANCELLED_REQUEST_LIMIT))
      .thenApply(r -> r.map(records -> new ArrayList<>(records.getRecords())));
  }

  private List<Request> getFirstRequestFromList(List<Result<List<Request>>> requestBatches) {
    return requestBatches.stream()
      .map(Result::value)
      .map(this::getFirstRequestOfEachItemInBatch)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private List<Request> getFirstRequestOfEachItemInBatch(List<Request> requestBatch) {
    return requestBatch.stream()
      .map(Request::getItemId)
      .filter(Objects::nonNull)
      .distinct()
      .map(itemId -> requestBatch.stream()
        .filter(request -> itemId.equals(request.getItemId()))
        .findFirst()
        .orElse(null))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<MultipleRecords<Request>>> findRequestsByCqlQuery(
          GetManyRecordsClient client, Result<CqlQuery> cqlQueryResult,
          PageLimit pageLimit) {

    return cqlQueryResult
      .after(query -> client.getMany(query, pageLimit))
      .thenApply(result -> result.next(this::mapResponseToRequest));
  }

  private CompletableFuture<Result<List<Request>>> fetchItemToRequest(
    Result<List<Request>> requestsResult, ItemRepository itemRepository) {

    return requestsResult.after(
      requests -> allOf(requests, request -> fetchItem(itemRepository, request)));
  }

  private Result<JsonObject> mapResultToJson(Result<List<Request>> requestsResult) {
    return requestsResult.map(this::toRequestsResponse);
  }

  private JsonObject toRequestsResponse(List<Request> requests) {
    final List<JsonObject> requestsRepresentations = requests.stream()
      .map(request -> new RequestRepresentation().extendedRepresentation(request))
      .collect(Collectors.toList());

    return new JsonObject()
      .put(REQUESTS_KEY, new JsonArray(requestsRepresentations))
      .put(TOTAL_RECORDS_KEY, requestsRepresentations.size());
  }

  private CompletableFuture<Result<Request>> fetchItem(ItemRepository itemRepository, Request request) {
    return CompletableFuture.completedFuture(Result.succeeded(request))
      .thenComposeAsync(result -> result.combineAfter(itemRepository::fetchFor, Request::withItem));
  }

  private Result<MultipleRecords<Request>> mapResponseToRequest(Response response) {
    return MultipleRecords.from(response, Request::from, REQUESTS_KEY);
  }
}
