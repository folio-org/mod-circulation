package org.folio.circulation.resources;

import static org.folio.circulation.domain.InstanceRequestItemsComparer.sortRequestQueues;
import static org.folio.circulation.domain.representations.RequestProperties.PROXY_USER_ID;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.*;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestByInstanceIdResource extends Resource {

  private final Logger log;
  private static final String ITEM_ID_FIELD = "itemId";
  private static final String REQUESTER_ID_FIELD = "requesterId";

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
    requestRelatedRecords.setInstanceLevelRequest(requestByInstanceIdRequest);

    finder.getItemsByInstanceId(requestByInstanceIdRequest.getInstanceId())
          .thenCompose(r -> r.after(items -> getRequestQueues(items,requestRelatedRecords, clients)))
          .thenApply(r -> r.next(items -> validateRequester(items, requestByInstanceIdRequest.getRequesterId())))
          .thenApply(r -> r.next(items -> segregateItemsList(items, requestRelatedRecords)))
          .thenApply(r -> r.next(RequestByInstanceIdResource::rankItemsByMatchingServicePoint))
          .thenCompose(r -> r.after(relatedRecords -> getLoanItems(relatedRecords, clients)))
          .thenApply( r -> r.next(loanItems -> combineWithUnavailableItems(loanItems, requestRelatedRecords)))
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

  private CompletableFuture<Result<Map<Item, DateTime>>> getLoanItems(
    InstanceRequestRelatedRecords instanceRequestPackage, Clients clients) {

    final List<Item> unsortedUnavailableItems = instanceRequestPackage.getUnsortedUnavailableItems();
    if (unsortedUnavailableItems == null ||
        unsortedUnavailableItems.isEmpty()) {
      return CompletableFuture.completedFuture(succeeded(null));
    }

    LoanRepository loanRepository = new LoanRepository(clients);
    Map<Item, CompletableFuture<Result<Loan>>> itemLoanFuturesMap = new HashMap<>();

    //Find request queues and loan items for each item
    for (Item item : unsortedUnavailableItems) {
      itemLoanFuturesMap.put(item, loanRepository.findOpenLoanForItem(item));
    }

    final Collection<CompletableFuture<Result<Loan>>> loanFutures = itemLoanFuturesMap.values();

    return CompletableFuture.allOf(loanFutures.toArray(new CompletableFuture[0]))
      .thenApply(dd -> {
        Map<Item, DateTime> itemDueDateMap = new HashMap<>();
        List<Item> itemsWithoutLoansList = new ArrayList<>();

        for (Map.Entry<Item, CompletableFuture<Result<Loan>>> entry : itemLoanFuturesMap.entrySet()) {
          Result<Loan> aLoanResult = entry.getValue().join();
          if (aLoanResult.succeeded() && aLoanResult.value() != null) {
            itemDueDateMap.put(aLoanResult.value().getItem(), aLoanResult.value().getDueDate());
          } else {
            itemsWithoutLoansList.add(entry.getKey());
          }
        }
        instanceRequestPackage.setItemsWithoutLoans(itemsWithoutLoansList);
        return succeeded(itemDueDateMap);
      });
  }

  private CompletableFuture<Result<Map<Item, RequestQueue>>> getRequestQueues(
    Collection<Item> items,
    InstanceRequestRelatedRecords instanceRequestPackage, Clients clients) {

    RequestQueueRepository queueRepository = RequestQueueRepository.using(clients);
    Map<Item, CompletableFuture<Result<RequestQueue>>> itemRequestQueueMap = new HashMap<>();

    for (Item item : items) {
      itemRequestQueueMap.put(item, queueRepository.getRequestQueueWithoutItemLookup(item.getItemId()));
    }

    final Collection<CompletableFuture<Result<RequestQueue>>> requestQueuesFutures = itemRequestQueueMap.values();

    //Collect the RequestQueue objects once they come back
    return CompletableFuture.allOf(requestQueuesFutures.toArray(new CompletableFuture[0]))
      .thenApply(x -> {
        Map<Item, RequestQueue> itemQueueMap = new HashMap<>();
        List<Item> itemsWithoutRequestQueues = new ArrayList<>();

        for (Map.Entry<Item, CompletableFuture<Result<RequestQueue>>> entry : itemRequestQueueMap.entrySet()) {
          Result<RequestQueue> requestQueueResult = entry.getValue().join();
          if (requestQueueResult.succeeded()) {
            itemQueueMap.put(entry.getKey(), requestQueueResult.value());
          } else {
            itemsWithoutRequestQueues.add(entry.getKey());
          }
        }
        if (itemsWithoutRequestQueues.size() == requestQueuesFutures.size()
          && (instanceRequestPackage.getSortedAvailableItems() == null || instanceRequestPackage.getSortedAvailableItems().isEmpty())) {
          //fail the requests when there are no items to make requests from.
          log.error("Failed to find request queues for all items of instanceId {}",
            items.toArray(new Item[0])[0].getInstanceId());
          return failed(new ServerErrorFailure("Unable to find an item to place a request"));
        }
        instanceRequestPackage.setItemsWithoutRequests(itemsWithoutRequestQueues);
        instanceRequestPackage.setItemRequestQueueMap(itemQueueMap);
        return succeeded(itemQueueMap);
    });
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequests(List<JsonObject> itemRequestRepresentations,
                                                                            Clients clients) {

    final RequestNoticeSender requestNoticeSender = RequestNoticeSender.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    
    final UpdateUponRequest updateUponRequest = new UpdateUponRequest(
        new UpdateItem(clients),
        new UpdateLoan(clients, loanRepository, loanPolicyRepository),
        new UpdateLoanActionHistory(clients));

    final CreateRequestService createRequestService = new CreateRequestService(
        RequestRepository.using(clients),
        new RequestPolicyRepository(clients),
        updateUponRequest,
        new RequestLoanValidator(loanRepository),
        requestNoticeSender);

    return placeRequest(itemRequestRepresentations, 0, createRequestService,
                        clients, loanRepository, new ArrayList<>());
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequest(List<JsonObject> itemRequests, int startIndex,
                                                                           CreateRequestService createRequestService, Clients clients,
                                                                           LoanRepository loanRepository, List<String> errors) {
    final UserRepository userRepository = new UserRepository(clients);

    log.debug("RequestByInstanceIdResource.placeRequest, startIndex={}, itemRequestSize={}", startIndex, itemRequests.size());
    if (startIndex >= itemRequests.size()) {

      String aggregateFailures = String.format("%n%s", String.join("%n", errors));

      return CompletableFuture.completedFuture(failed(new ServerErrorFailure(
        "Failed to place a request for the title. Reasons: " + aggregateFailures)));
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
            log.debug("RequestByInstanceIdResource.placeRequest: succeeded creating request for item {}",
                currentItemRequest.getString(ITEM_ID_FIELD));
            return CompletableFuture.completedFuture(r);
          } else {
            String reason = getErrorMessage(r.cause());
            errors.add(reason);

            log.debug("Failed to create request for item {} with reason: {}", currentItemRequest.getString(ITEM_ID_FIELD), reason);
            return placeRequest(itemRequests, startIndex +1, createRequestService, clients, loanRepository, errors);
          }
        });
  }

  public static Result<InstanceRequestRelatedRecords> rankItemsByMatchingServicePoint(InstanceRequestRelatedRecords record) {

    final Collection<Item> unsortedAvailableItems = record.getUnsortedAvailableItems();
    final UUID pickupServicePointId = record.getInstanceLevelRequest().getPickupServicePointId();

    return of(() -> {

      Map<Boolean, List<Item>> itemsPartitionedByLocationServedByPickupPoint = unsortedAvailableItems.stream()
        .collect(Collectors.partitioningBy(i -> Optional.ofNullable(i.getLocation())
          .map(location -> location.homeLocationIsServedBy(pickupServicePointId))
          .orElse(false)));

      final List<Item> rankedItems = new LinkedList<>();

      //Compose the final list of Items with the matchingItems (items that has matching service pointID) on top.
      rankedItems.addAll(itemsPartitionedByLocationServedByPickupPoint.get(true));
      rankedItems.addAll(itemsPartitionedByLocationServedByPickupPoint.get(false));

      record.setSortedAvailableItems(rankedItems);

      return record;
    });
  }

  static Result<LinkedList<JsonObject>> instanceToItemRequests( InstanceRequestRelatedRecords requestRecords) {

    final RequestByInstanceIdRequest requestByInstanceIdRequest = requestRecords.getInstanceLevelRequest();
    final List<Item> combinedItems = requestRecords.getCombinedSortedItemsList();

    if (combinedItems == null || combinedItems.isEmpty()) {
      return failedValidation("Cannot create request objects when items list is null or empty", "items", "null");    }

    RequestType[] types = RequestType.values();
    LinkedList<JsonObject> requests = new LinkedList<>();

    final String defaultFulfilmentPreference = "Hold Shelf";

    for (Item item: combinedItems) {
      for (RequestType reqType : types) {
        if (reqType != RequestType.NONE) {

          JsonObject requestBody = new JsonObject();

          write(requestBody, ITEM_ID_FIELD, item.getItemId());
          write(requestBody, "requestDate",
            requestByInstanceIdRequest.getRequestDate().toString(ISODateTimeFormat.dateTime()));
          write(requestBody, REQUESTER_ID_FIELD, requestByInstanceIdRequest.getRequesterId().toString());
          write(requestBody, "pickupServicePointId",
            requestByInstanceIdRequest.getPickupServicePointId().toString());
          write(requestBody, "fulfilmentPreference", defaultFulfilmentPreference);
          write(requestBody, "requestType", reqType.getValue());
          if (requestByInstanceIdRequest.getRequestExpirationDate() != null) {
            write(requestBody, "requestExpirationDate",
              requestByInstanceIdRequest.getRequestExpirationDate().toString(ISODateTimeFormat.dateTime()));
          }
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

    log.debug("RequestByInstanceIdResource.segregateItemsList: Found {} items", items.size());

    Map<Boolean, List<Item>> partitions = items.stream()
      .collect(Collectors.partitioningBy(Item::isAvailable));

    requestRelatedRecords.setUnsortedAvailableItems(partitions.get(true));
    requestRelatedRecords.setUnsortedUnavailableItems(partitions.get(false));

    return succeeded(requestRelatedRecords);
  }

  private Result<InstanceRequestRelatedRecords> combineWithUnavailableItems(
    Map<Item, DateTime> itemDueDateMap,
    InstanceRequestRelatedRecords records){

    return of(() -> {
        Map<Item, RequestQueue> itemQueueMap = records.getItemRequestQueueMap();

        if (itemDueDateMap == null && itemQueueMap == null) {
          return records;
        }

        //transform itemQueueMap to itemQueueSizeMap
        Map<Item, Integer> itemQueueSizeMap = itemQueueMap.entrySet()
          .stream()
          .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> entry.getValue().size()
            ));

        //Sort the itemQueueSize map
        Map<Item, Integer> sortedMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap,
          records.getInstanceLevelRequest().getPickupServicePointId());

        ArrayList<Item> finalOrdedList = new ArrayList<>(sortedMap.keySet());

        //put the items that it wasn't able to retrieve loans or RequestQueues for on the bottom of the list
        if (records.getItemsWithoutLoans() != null)
          finalOrdedList.addAll(records.getItemsWithoutLoans());
        if (records.getItemsWithoutRequests() != null)
          finalOrdedList.addAll(records.getItemsWithoutRequests());
        records.setSortedUnavailableItems(finalOrdedList);
      return records;
    });
  }

  private Result<Collection<Item>> validateRequester(Map<Item, RequestQueue> itemRequestQueueMap, UUID requesterId) {
    if (!itemRequestQueueMap.isEmpty()) {
      Collection<RequestQueue> requestQueues = itemRequestQueueMap.values();
      for (RequestQueue queue : requestQueues) {
        final Optional<Request> matchingRequest = queue.getRequests()
          .stream()
          .filter(request -> request.isOpen()
                           && Objects.equals(request.asJson().getString(REQUESTER_ID_FIELD),
                                             requesterId.toString())
          ).findFirst();

        if (matchingRequest.isPresent()) {
            Map<String, String> parameters = new HashMap<>();
            parameters.put(REQUESTER_ID_FIELD, requesterId.toString());
            parameters.put(ITEM_ID_FIELD, matchingRequest.get().asJson().getString(ITEM_ID_FIELD));
            String message = "This requester already has an open request for a copy of this title";
            return failedValidation(new ValidationError(message, parameters));
          }
        }
      }
    return of(itemRequestQueueMap::keySet);
  }

  private ProxyRelationshipValidator createProxyRelationshipValidator(
    JsonObject representation,
    Clients clients) {

    return new ProxyRelationshipValidator(clients, () ->
      singleValidationError("proxyUserId is not valid",
        PROXY_USER_ID, representation.getString(PROXY_USER_ID)));
  }

  static String getErrorMessage(HttpFailure failure) {
    String reason = "";

    if (failure instanceof ServerErrorFailure ){
      reason = ((ServerErrorFailure) failure).reason;
    } else if (failure instanceof ValidationErrorFailure){
      reason = failure.toString();
    } else if (failure instanceof BadRequestFailure){
      reason = ((BadRequestFailure) failure).getReason();
    } else if (failure instanceof ForwardOnFailure) {
      reason = ((ForwardOnFailure) failure).getFailureResponse().getBody();
    }
    return reason;
  }
}
