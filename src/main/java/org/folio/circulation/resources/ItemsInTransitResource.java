package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.InTransitReportEntry;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.ResultItemContext;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.representations.ItemReportRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.request.RequestHelper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.CqlSortBy.ascending;

public class ItemsInTransitResource extends Resource {

  private final String rootPath;

  public ItemsInTransitResource(String rootPath, HttpClient client) {
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

    final LoanRepository loanRepository = new LoanRepository(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);

    itemRepository.getAllItemsByField("status.name", IN_TRANSIT.getValue())
      .thenComposeAsync(r -> r.after(resultItemContext ->
        fetchInTransitReportEntry(resultItemContext, itemRepository, servicePointRepository)))
      .thenComposeAsync(r -> loanRepository.fetchLoans(r.value()))
      .thenComposeAsync(r -> findRequestsByItemsIds(requestRepository, r.value()))
      .thenApply(this::mapResultToJson)
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  public CompletableFuture<Result<List<InTransitReportEntry>>> fetchInTransitReportEntry(ResultItemContext resultItemContext,
                                                                                       ItemRepository itemRepository,
                                                                                       ServicePointRepository servicePointRepository) {
    List<InTransitReportEntry> inTransitReportEntries = resultItemContext.getResultListOfItems().stream()
      .flatMap(records -> records.value().getRecords()
        .stream()).map(item -> fetchRelatedRecords(itemRepository, servicePointRepository, item))
      .map(CompletableFuture::join)
      .map(item -> new InTransitReportEntry(item.value()))
      .collect(Collectors.toList());

    return CompletableFuture.completedFuture(Result.succeeded(inTransitReportEntries));
  }

  private CompletableFuture<Result<Item>> fetchRelatedRecords(ItemRepository itemRepository,
                                                              ServicePointRepository servicePointRepository,
                                                              Item item) {
    return CompletableFuture.completedFuture(Result.succeeded(item))
      .thenComposeAsync(itemRepository::fetchItemRelatedRecords)
      .thenComposeAsync(result -> result
        .combineAfter(it -> servicePointRepository
          .getServicePointById(it.getInTransitDestinationServicePointId()), Item::updateDestinationServicePoint));
  }

  private CompletableFuture<Result<List<InTransitReportEntry>>> findRequestsByItemsIds(RequestRepository requestRepository,
                                                                                       List<InTransitReportEntry> inTransitReportEntryList) {
    return mapToItemIdList(inTransitReportEntryList)
      .thenComposeAsync(r -> r.after(RequestHelper::mapItemIdsInBatchItemIds))
      .thenComposeAsync(r -> getInTransitRequestByItemsIds(requestRepository, r.value()))
      .thenComposeAsync(r -> setRequestToInTransitReportEntry(inTransitReportEntryList, r.value()));
  }

  private CompletableFuture<Result<List<Result<MultipleRecords<Request>>>>> getInTransitRequestByItemsIds(RequestRepository requestRepository,
                                                                                                          List<List<String>> batchItemIds) {
    List<Result<MultipleRecords<Request>>> inTransitRequest = getInTransitRequest(requestRepository, batchItemIds);
    return CompletableFuture.completedFuture(Result.succeeded(inTransitRequest));
  }

  private List<Result<MultipleRecords<Request>>> getInTransitRequest(RequestRepository requestRepository,
                                                                     List<List<String>> batchItemIds) {
    return batchItemIds.stream()
      .map(itemIds -> {
        final Result<CqlQuery> statusQuery = exactMatchAny("status", RequestStatus.openStates());
        final Result<CqlQuery> itemIdsQuery = exactMatchAny("itemId", itemIds);

        Result<CqlQuery> cqlQueryResult = statusQuery.combine(itemIdsQuery, CqlQuery::and)
          .map(q -> q.sortBy(ascending("position")));

        return requestRepository.findBy(cqlQueryResult, itemIds.size());
      })
      .map(CompletableFuture::join)
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<List<String>>> mapToItemIdList(List<InTransitReportEntry> inTransitReportEntryList) {
    List<String> itemIds = inTransitReportEntryList.stream()
      .map(InTransitReportEntry::getItem)
      .filter(Objects::nonNull)
      .map(Item::getItemId)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    return CompletableFuture.completedFuture(Result.succeeded(itemIds));
  }

  private CompletableFuture<Result<List<InTransitReportEntry>>> setRequestToInTransitReportEntry(
    List<InTransitReportEntry> inTransitReportEntryList,
    List<Result<MultipleRecords<Request>>> requestList) {

    Map<String, Request> itemRequestsMap = mapToItemRequestMap(requestList);
    setRequestToInTransitReportEntry(inTransitReportEntryList, itemRequestsMap);
    return CompletableFuture.completedFuture(Result.succeeded(inTransitReportEntryList));
  }

  private void setRequestToInTransitReportEntry(List<InTransitReportEntry> inTransitReportEntryList,
                                                 Map<String, Request> itemRequestsMap) {
    inTransitReportEntryList.stream().filter(inTransitReportEntry -> itemRequestsMap
      .containsKey(inTransitReportEntry.getItem().getItemId()))
      .forEach(inTransitReportEntry -> inTransitReportEntry
        .setRequest(itemRequestsMap.get(inTransitReportEntry.getItem().getItemId())));
  }

  private Map<String, Request> mapToItemRequestMap(List<Result<MultipleRecords<Request>>> requestList) {
    return requestList.stream()
      .flatMap(records -> records.value().getRecords().stream())
      .collect(Collectors.toMap(Request::getItemId, Function.identity()));
  }

  private Result<JsonObject> mapResultToJson
    (Result<List<InTransitReportEntry>> inTransitReportEntry) {

    return inTransitReportEntry.map(resultList -> resultList
      .stream().map(itemAndRelatedRecord -> new ItemReportRepresentation()
        .createItemReport(itemAndRelatedRecord))
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add)))
      .next(jsonArray -> Result.succeeded(new JsonObject()
        .put("items", jsonArray)
        .put("totalRecords", jsonArray.size())));
  }

}
