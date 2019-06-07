package org.folio.circulation.resources;

import static org.folio.circulation.domain.representations.RequestProperties.PROXY_USER_ID;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.CreateRequestService;
import org.folio.circulation.domain.InstanceRequestRelatedRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateLoan;
import org.folio.circulation.domain.UpdateLoanActionHistory;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestByInstanceIdResource extends Resource {

  private final Logger log;

  public RequestByInstanceIdResource(HttpClient client) {
    super(client);
    log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/requests/instances", router);

    routeRegistration.create(this::createInstanceLevelRequests);
  }

  private void createInstanceLevelRequests(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);

    final Result<RequestByInstanceIdRequest> requestByInstanceIdRequestResult =
      RequestByInstanceIdRequest.from(routingContext.getBodyAsJson());

    if(requestByInstanceIdRequestResult.failed()) {
      ResponseWritableResult<Object> failed = Result.failed(requestByInstanceIdRequestResult.cause());
      failed.writeTo(routingContext.response());
      return;
    }

    final RequestByInstanceIdRequest requestByInstanceIdRequest =
      requestByInstanceIdRequestResult.value();

    final ItemByInstanceIdFinder finder = new ItemByInstanceIdFinder(clients.holdingsStorage(), itemRepository);

    final InstanceRequestRelatedRecords requestRelatedRecords = new InstanceRequestRelatedRecords();
    requestRelatedRecords.setRequestByInstanceIdRequest(requestByInstanceIdRequest);

    finder.getItemsByInstanceId(requestByInstanceIdRequest.getInstanceId())
      .thenApply(r -> r.next(items -> segregateItemsList(items, requestRelatedRecords)))
      .thenApply(r -> r.next(RequestByInstanceIdResource::rankItemsByMatchingServicePoint))
      .thenCompose(r -> r.after(relatedRecords -> combineWithUnavailableItems(relatedRecords, clients)))
      .thenApply( r -> r.next(RequestByInstanceIdResource::instanceToItemRequests))
      .thenCompose( r -> r.after( requests -> placeRequests(requests, clients)))
      .thenApply(r -> r.map(RequestAndRelatedRecords::getRequest))
      .thenApply(r -> r.map(new RequestRepresentation()::extendedRepresentation))
      .thenApply(CreatedJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()))
      .exceptionally( err -> {
          String reason = "Error processing title-level request";
          log.error(reason, err);
          ServerErrorResponse.internalError(routingContext.response(), reason);
          return null;
      });
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequests(List<JsonObject> itemRequestRepresentations,
                                                                            Clients clients) {

    final RequestNoticeSender requestNoticeSender = RequestNoticeSender.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    final CreateRequestService createRequestService = new CreateRequestService(
      RequestRepository.using(clients),
      new UpdateItem(clients),
      new UpdateLoanActionHistory(clients),
      new UpdateLoan(clients, loanRepository, new LoanPolicyRepository(clients)),
      new RequestPolicyRepository(clients),
      loanRepository, requestNoticeSender);

    return placeRequest(itemRequestRepresentations, 0, createRequestService, clients, loanRepository);
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequest(List<JsonObject> itemRequests, int startIndex,
                                                                           CreateRequestService createRequestService, Clients clients,
                                                                           LoanRepository loanRepository) {
    final UserRepository userRepository = new UserRepository(clients);

    if (startIndex >= itemRequests.size()) {
      return CompletableFuture.completedFuture(failed(new ServerErrorFailure(
        "Failed to place a request for the title")));
    }

    JsonObject currentItemRequest = itemRequests.get(startIndex);

    final RequestFromRepresentationService requestFromRepresentationService =
      new RequestFromRepresentationService(
        new ItemRepository(clients, true, false, false),
        RequestQueueRepository.using(clients),
        userRepository,
        loanRepository,
        new ServicePointRepository(clients),
        createProxyRelationshipValidator(currentItemRequest, clients),
        new ServicePointPickupLocationValidator()
      );

    return requestFromRepresentationService.getRequestFrom(currentItemRequest)
      .thenCompose(r -> r.after(createRequestService::createRequest))
      .thenCompose(r -> {
          if (r.succeeded()) {
            return CompletableFuture.completedFuture(r);
          } else {
            log.debug("Failed to create request for {}", currentItemRequest.getString("id"));
            return placeRequest(itemRequests, startIndex +1, createRequestService, clients, loanRepository);
          }
        });
  }

  public static Result<InstanceRequestRelatedRecords> rankItemsByMatchingServicePoint(InstanceRequestRelatedRecords record) {

    final Collection<Item> unsortedAvailableItems = record.getUnsortedAvailableItems();
    final UUID pickupServicePointId = record.getRequestByInstanceIdRequest().getPickupServicePointId();

    return of(() -> {

      Map<Boolean, List<Item>> itemsPartitionedByLocationServedByPickupPoint = unsortedAvailableItems.stream()
        .collect(Collectors.partitioningBy(i -> i.homeLocationIsServedBy(pickupServicePointId)));

      final List<Item> rankedItems = new LinkedList<>();

      //Compose the final list of Items with the matchingItems (items that has matching service pointID) on top.
      rankedItems.addAll(itemsPartitionedByLocationServedByPickupPoint.get(true));
      rankedItems.addAll(itemsPartitionedByLocationServedByPickupPoint.get(false));

      record.setSortedAvailableItems(rankedItems);

      return record;
    });
  }

  static Result<LinkedList<JsonObject>> instanceToItemRequests( InstanceRequestRelatedRecords requestRecords) {

    final RequestByInstanceIdRequest requestByInstanceIdRequest = requestRecords.getRequestByInstanceIdRequest();
    final List<Item> combinedItems = requestRecords.getCombineItemsList();

    if (combinedItems == null || combinedItems.isEmpty()) {
      return failedValidation("Cannot create request objects when items list is null or empty", "items", "null");    }

    RequestType[] types = RequestType.values();
    LinkedList<JsonObject> requests = new LinkedList<>();

    final String defaultFulfilmentPreference = "Hold Shelf";

    for (Item item: combinedItems) {
      for (RequestType reqType : types) {
        if (reqType != RequestType.NONE) {

          JsonObject requestBody = new JsonObject();
          requestBody.put("itemId", item.getItemId());
          requestBody.put("requestDate", requestByInstanceIdRequest.getRequestDate().toString(ISODateTimeFormat.dateTime()));
          requestBody.put("requesterId", requestByInstanceIdRequest.getRequesterId().toString());
          requestBody.put("pickupServicePointId", requestByInstanceIdRequest.getPickupServicePointId().toString());
          requestBody.put("fulfilmentPreference", defaultFulfilmentPreference);
          requestBody.put("requestExpirationDate",
            requestByInstanceIdRequest.getRequestExpirationDate().toString(ISODateTimeFormat.dateTime()));
          requestBody.put("requestType", reqType.name());

          requests.add(requestBody);
        }
      }
    }
    return succeeded(requests);
  }

  private Result<InstanceRequestRelatedRecords> segregateItemsList(Collection<Item> items,
                                                                          InstanceRequestRelatedRecords requestRelatedRecords ){
    if (items == null ||items.isEmpty()) {
      return failedValidation("Items list is null or empty", "items", "null");
    }

    Map<Boolean, List<Item>> partitions = items.stream()
      .collect(Collectors.partitioningBy(Item::isAvailable));

    requestRelatedRecords.setUnsortedAvailableItems(partitions.get(true));
    requestRelatedRecords.setUnsortedUnavailableItems(partitions.get(false));

    return succeeded(requestRelatedRecords);
  }

  private CompletableFuture<Result<InstanceRequestRelatedRecords>> combineWithUnavailableItems(
                                              InstanceRequestRelatedRecords records, Clients clients){

    RequestQueueRepository queueRepository = RequestQueueRepository.using(clients);

    final List<Item> unsortedUnavailableItems = records.getUnsortedUnavailableItems();

    Map<Item, CompletableFuture<Result<RequestQueue>>> itemRequestQueueMap = new HashMap<>();
    if (unsortedUnavailableItems == null || unsortedUnavailableItems.isEmpty()) {
      return CompletableFuture.completedFuture(succeeded(records));
    }

    for (Item item : unsortedUnavailableItems) {
      itemRequestQueueMap.put(item, queueRepository.getRequestQueueWithoutItemLookup(item.getItemId()));
    }

    final Collection<CompletableFuture<Result<RequestQueue>>> requestQueueFutures = itemRequestQueueMap.values();

    //Collect the RequestQueue objects once they come back
    return CompletableFuture.allOf(requestQueueFutures.toArray(new CompletableFuture[requestQueueFutures.size()]))
      .thenApply(x -> {
        Map<Item, RequestQueue> itemQueueSizeMap = new HashMap<>();
        List<Item> failedQueuesItemList = new LinkedList<>(); //to preserve the order it was found.

        for (Map.Entry<Item, CompletableFuture<Result<RequestQueue>>> entry : itemRequestQueueMap.entrySet()) {
          Result<RequestQueue> requestQueueResult = entry.getValue().join();
          if (requestQueueResult.succeeded()) {
            itemQueueSizeMap.put(entry.getKey(), requestQueueResult.value());
          } else {
            failedQueuesItemList.add(entry.getKey());
          }
        }

        if (failedQueuesItemList.size() == requestQueueFutures.size()) {
          log.error("Failed to find request queues for all items of instanceId {}",
                        unsortedUnavailableItems.get(0).getInstanceId());
          return failed(new ServerErrorFailure("Unable to find an item to place a request"));
        }
        //Sort the map
        Map<Item, RequestQueue> sortedMap = sortRequestQueues(itemQueueSizeMap);

        LinkedList<Item> finalOrdedList = new LinkedList<>();

        finalOrdedList.addAll(sortedMap.keySet());
        //put the items that weren't able to retrieve RequestQueus for on the bottom of the listt
        finalOrdedList.addAll(failedQueuesItemList);

        records.setSortedUnavailableItems(finalOrdedList);

        return succeeded(records);
      });
  }

  private static Map<Item, RequestQueue> sortRequestQueues(Map<Item, RequestQueue> unsortedItems) {
    return unsortedItems
      .entrySet()
      .stream()
      .sorted(compareQueueLengths())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
        (oldValue, newValue) -> oldValue, (LinkedHashMap::new)));
  }

  private static Comparator<Map.Entry<Item, RequestQueue>> compareQueueLengths() {
    // Sort the list
    return (q1, q2) -> {
      RequestQueue queue1 = q1.getValue();
      RequestQueue queue2 = q2.getValue();

      return queue1.size() - queue2.size();
    };
  }

  private ProxyRelationshipValidator createProxyRelationshipValidator(
    JsonObject representation,
    Clients clients) {

    return new ProxyRelationshipValidator(clients, () ->
      singleValidationError("proxyUserId is not valid",
        PROXY_USER_ID, representation.getString(PROXY_USER_ID)));
  }
}
