package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemAndRelatedRecords;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.ResultItemContext;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.representations.ItemReportRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.WebContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;

public class ItemsInTransitResource extends Resource {

  private static final int OPEN_REQUEST_LIMIT = 1;
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

    final CollectionResourceClient loansStorageClient = clients.loansStorage();
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);

    itemRepository.getAllItemsByField("status.name", IN_TRANSIT.getValue())
      .thenComposeAsync(r -> r.after(resultItemContext ->
        fetchItemRelatedRecords(resultItemContext, itemRepository, servicePointRepository)))
      .thenComposeAsync(r -> r.after(itemAndRelatedRecords ->
        fetchLoans(loansStorageClient, servicePointRepository, itemAndRelatedRecords)))
      .thenComposeAsync(r -> findRequestsByItemsIds(requestRepository, r.value()))
      .thenApply(this::mapResultToJson)
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<List<ItemAndRelatedRecords>>> fetchLoans(CollectionResourceClient loanStorage,
                                                                            ServicePointRepository servicePointRepository,
                                                                            List<ItemAndRelatedRecords> itemAndRelatedRecords) {
    final List<String> itemsToFetchLoansFor = itemAndRelatedRecords.stream()
      .filter(Objects::nonNull)
      .map(itemAndRelatedRecord -> itemAndRelatedRecord.getItem().getItemId())
      .filter(Objects::nonNull)
      .distinct()
      .collect(Collectors.toList());

    if (itemsToFetchLoansFor.isEmpty()) {
      return completedFuture(succeeded(itemAndRelatedRecords));
    }

    final Result<CqlQuery> statusQuery = exactMatch("itemStatus", IN_TRANSIT.getValue());
    final Result<CqlQuery> itemIdQuery = exactMatchAny("itemId", itemsToFetchLoansFor);

    CompletableFuture<Result<MultipleRecords<Loan>>> multipleRecordsLoans =
      statusQuery.combine(
        itemIdQuery, CqlQuery::and)
        .after(q -> loanStorage.getMany(q, itemAndRelatedRecords.size()))
        .thenApply(result -> result.next(this::mapResponseToLoans));

    return multipleRecordsLoans.thenCompose(multiLoanRecordsResult ->
      multiLoanRecordsResult.after(servicePointRepository::findServicePointsForLoans))
      .thenApply(multipleLoansResult -> multipleLoansResult.next(
        loans -> matchLoansToRequests(itemAndRelatedRecords, loans)));
  }

  private Result<List<ItemAndRelatedRecords>> matchLoansToRequests(
    List<ItemAndRelatedRecords> multipleRequests,
    MultipleRecords<Loan> loans) {

    return of(() ->
      multipleRequests.stream()
        .map(itemAndRelatedRecord -> matchLoansToRequest(itemAndRelatedRecord, loans))
        .collect(Collectors.toList()));
  }

  private ItemAndRelatedRecords matchLoansToRequest(
    ItemAndRelatedRecords request,
    MultipleRecords<Loan> loans) {

    final Map<String, Loan> loanMap = loans.toMap(Loan::getItemId);
    request
      .setLoan(loanMap.getOrDefault(request.getItem().getItemId(), null));
    return request;
  }

  private Result<MultipleRecords<Loan>> mapResponseToLoans(Response response) {
    return MultipleRecords.from(response, Loan::from, "loans");
  }

  public CompletableFuture<Result<List<ItemAndRelatedRecords>>> fetchItemRelatedRecords(ResultItemContext resultItemContext,
                                                                                        ItemRepository itemRepository,
                                                                                        ServicePointRepository servicePointRepository) {
    List<ItemAndRelatedRecords> itemAndRelatedRecords = resultItemContext.getResultListOfItems().stream()
      .flatMap(records -> records.value().getRecords()
        .stream()).map(item -> fetchRelatedRecords(itemRepository, servicePointRepository, item))
      .map(CompletableFuture::join)
      .map(item -> new ItemAndRelatedRecords(item.value()))
      .collect(Collectors.toList());

    return CompletableFuture.completedFuture(Result.succeeded(itemAndRelatedRecords));
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

  private CompletableFuture<Result<List<ItemAndRelatedRecords>>> findRequestsByItemsIds(RequestRepository requestRepository,
                                                                                        List<ItemAndRelatedRecords> batchItemIds) {
    List<Result<MultipleRecords<Request>>> requestList = batchItemIds.stream()
      .map(itemAndRelatedRecords -> {
        final Result<CqlQuery> statusQuery = exactMatchAny("status", RequestStatus.openStates());
        final Result<CqlQuery> itemIdsQuery = exactMatch("itemId", itemAndRelatedRecords.getItem().getItemId());

        Result<CqlQuery> cqlQueryResult = statusQuery.combine(itemIdsQuery, CqlQuery::and);

        return requestRepository.findByWithRelatedRecords(cqlQueryResult, OPEN_REQUEST_LIMIT);
      })
      .map(CompletableFuture::join)
      .collect(Collectors.toList());

    setFirstRequest(batchItemIds, requestList);
    return CompletableFuture.completedFuture(Result.succeeded(batchItemIds));
  }

  private void setFirstRequest(List<ItemAndRelatedRecords> batchItemIds,
                               List<Result<MultipleRecords<Request>>> requestList) {
    List<Request> firstRequestFromList = getFirstRequestFromList(requestList);
    Map<String, Request> itemRequestsMap = new HashMap<>();
    firstRequestFromList.forEach(request -> itemRequestsMap.put(request.getItemId(), request));
    batchItemIds.stream().filter(itemAndRelatedRecords -> itemRequestsMap
      .containsKey(itemAndRelatedRecords.getItem().getItemId()))
      .forEach(itemAndRelatedRecords -> itemAndRelatedRecords
          .setRequest(itemRequestsMap.get(itemAndRelatedRecords.getItem().getItemId())));
  }

  private List<Request> getFirstRequestFromList(List<Result<MultipleRecords<Request>>> multipleRecordsRequestList) {
    return multipleRecordsRequestList.stream()
      .map(r -> r.value().getRecords().stream().findFirst())
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  private Result<JsonObject> mapResultToJson
    (Result<List<ItemAndRelatedRecords>> itemAndRelatedRecords) {

    return itemAndRelatedRecords.map(resultList -> resultList
      .stream().map(itemAndRelatedRecord -> new ItemReportRepresentation()
        .createItemReport(itemAndRelatedRecord))
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add)))
      .next(jsonArray -> Result.succeeded(new JsonObject()
        .put("items", jsonArray)
        .put("totalRecords", jsonArray.size())));
  }

}
