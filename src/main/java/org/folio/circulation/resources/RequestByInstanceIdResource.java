package org.folio.circulation.resources;

import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.InstanceRequestItemsComparer.sortRequestQueues;
import static org.folio.circulation.domain.RequestFulfilmentPreference.HOLD_SHELF;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.PAGE;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.domain.representations.RequestProperties.FULFILMENT_PREFERENCE;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_ID;
import static org.folio.circulation.domain.representations.RequestProperties.PROXY_USER_ID;
import static org.folio.circulation.domain.representations.RequestProperties.REQUESTER_ID;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_LEVEL;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.resources.RequestBlockValidators.regularRequestBlockValidators;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CreateRequestService;
import org.folio.circulation.domain.InstanceRequestRelatedRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestFulfilmentPreference;
import org.folio.circulation.domain.RequestLevel;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateLoan;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.UpdateUponRequest;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.resources.handlers.error.FailFastErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.ItemForTlrService;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.request.RequestRelatedRepositories;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestByInstanceIdResource extends Resource {

  private static final List<RequestType> ORDERED_REQUEST_TYPES = List.of(PAGE, RECALL, HOLD);
  private static final RequestFulfilmentPreference DEFAULT_FULFILMENT_PREFERENCE = HOLD_SHELF;

  private final Logger log;

  public RequestByInstanceIdResource(HttpClient client) {
    super(client);
    log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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

    RequestRelatedRepositories repositories = new RequestRelatedRepositories(clients);
    final var itemRepository = repositories.getItemRepository();

    final var itemFinder = new ItemByInstanceIdFinder(clients.holdingsStorage(), itemRepository);
    final var eventPublisher = new EventPublisher(routingContext);

    final var requestBody = routingContext.getBodyAsJson();

    new ConfigurationRepository(clients).lookupTlrSettings()
      .thenCompose(r -> r.after(config -> buildAndPlaceRequests(clients, eventPublisher,
        repositories, itemFinder, config, requestBody)))
      .thenApply(r -> r.map(RequestAndRelatedRecords::getRequest))
      .thenApply(r -> r.map(new RequestRepresentation()::extendedRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::created))
      .thenAccept(context::writeResultToHttpResponse)
      .exceptionally(err -> {
         String reason = "Error processing instance-level request";
         log.error(reason, err);
         ServerErrorResponse.internalError(routingContext.response(), reason);
         return null;
      });
  }

  private CompletableFuture<Result<InstanceRequestRelatedRecords>> getPotentialItems(
    ItemByInstanceIdFinder finder, InstanceRequestRelatedRecords requestRelatedRecords,
    RequestRelatedRepositories repositories) {

    final var loanRepository = repositories.getLoanRepository();
    final var requestQueueRepository = repositories.getRequestQueueRepository();

    return finder.getItemsByInstanceId(requestRelatedRecords.getInstanceLevelRequest().getInstanceId())
      .thenApply(r -> r.next(this::validateItems))
      .thenCompose(r -> r.after(items -> getRequestQueues(items,
        requestRelatedRecords, requestQueueRepository)))
      .thenApply(r -> r.next(items -> validateRequester(items, requestRelatedRecords)))
      .thenApply(r -> r.next(items -> segregateItemsList(requestRelatedRecords)))
      .thenApply(r -> r.next(RequestByInstanceIdResource::rankItemsByMatchingServicePoint))
      .thenCompose(r -> r.after(relatedRecords -> getLoanItems(relatedRecords, loanRepository)))
      .thenApply(r -> r.next(loanItems -> combineWithUnavailableItems(loanItems, requestRelatedRecords)));
  }

  private CompletableFuture<Result<Map<Item, ZonedDateTime>>> getLoanItems(
    InstanceRequestRelatedRecords instanceRequestPackage, LoanRepository loanRepository) {

    final List<Item> unsortedUnavailableItems = instanceRequestPackage.getUnsortedUnavailableItems();
    if (unsortedUnavailableItems == null ||
        unsortedUnavailableItems.isEmpty()) {
      return CompletableFuture.completedFuture(succeeded(null));
    }

    Map<Item, CompletableFuture<Result<Loan>>> itemLoanFuturesMap = new HashMap<>();

    //Find request queues and loan items for each item
    for (Item item : unsortedUnavailableItems) {
      itemLoanFuturesMap.put(item, loanRepository.findOpenLoanForItem(item));
    }

    final Collection<CompletableFuture<Result<Loan>>> loanFutures = itemLoanFuturesMap.values();

    return CompletableFuture.allOf(loanFutures.toArray(new CompletableFuture[0]))
      .thenApply(dd -> {
        Map<Item, ZonedDateTime> itemDueDateMap = new HashMap<>();
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
    Collection<Item> items, InstanceRequestRelatedRecords instanceRequestPackage,
    RequestQueueRepository requestQueueRepository) {

    Map<Item, CompletableFuture<Result<RequestQueue>>> itemRequestQueueMap = new HashMap<>();

    instanceRequestPackage.setAllUnsortedItems(items);

    for (Item item : items) {
      itemRequestQueueMap.put(item, requestQueueRepository.getRequestQueueWithoutItemLookup(item.getItemId()));
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
            instanceRequestPackage.getInstanceLevelRequest().getInstanceId());
          return failedDueToServerError("Unable to find an item to place a request");
        }
        instanceRequestPackage.setItemsWithoutRequests(itemsWithoutRequestQueues);
        instanceRequestPackage.setItemRequestQueueMap(itemQueueMap);
        return succeeded(itemQueueMap);
    });
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> buildAndPlaceRequests(
    Clients clients, EventPublisher eventPublisher, RequestRelatedRepositories repositories,
    ItemByInstanceIdFinder itemFinder, TlrSettingsConfiguration tlrConfig, JsonObject requestBody) {

    return buildRequests(requestBody, tlrConfig, itemFinder, repositories)
      .thenCompose(r -> r.after(requests -> placeRequests(clients, eventPublisher, repositories,
        itemFinder, requests)));
  }

  private CompletableFuture<Result<List<JsonObject>>> buildRequests(
    JsonObject requestBody, TlrSettingsConfiguration tlrConfig, ItemByInstanceIdFinder itemFinder,
    RequestRelatedRepositories repositories) {

    return tlrConfig.isTitleLevelRequestsFeatureEnabled()
      ? buildTitleLevelRequests(requestBody)
      : buildItemLevelRequests(requestBody, itemFinder, repositories);
  }

  private CompletableFuture<Result<List<JsonObject>>> buildTitleLevelRequests(
    JsonObject requestRepresentation) {

    return ofAsync(() -> ORDERED_REQUEST_TYPES.stream()
      .map(requestType -> requestRepresentation.copy()
        .put(REQUEST_TYPE, requestType.getValue())
        .put(REQUEST_LEVEL, RequestLevel.TITLE.getValue())
        .put(FULFILMENT_PREFERENCE, DEFAULT_FULFILMENT_PREFERENCE.getValue()))
      .collect(toList())
    );
  }

  private CompletableFuture<Result<List<JsonObject>>> buildItemLevelRequests(
    JsonObject requestBody, ItemByInstanceIdFinder itemFinder,
    RequestRelatedRepositories repositories) {

    return RequestByInstanceIdRequest.from(requestBody.put(REQUEST_LEVEL, RequestLevel.ITEM.getValue()))
      .map(InstanceRequestRelatedRecords::new)
      .after(instanceRequest -> getPotentialItems(itemFinder, instanceRequest, repositories))
      .thenApply( r -> r.next(RequestByInstanceIdResource::instanceToItemRequests));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequests(
    Clients clients, EventPublisher eventPublisher, RequestRelatedRepositories repositories,
    ItemByInstanceIdFinder itemFinder, List<JsonObject> requestRepresentations) {

    final var itemRepository = repositories.getItemRepository();
    final var loanRepository = repositories.getLoanRepository();
    final var loanPolicyRepository = repositories.getLoanPolicyRepository();
    final var requestRepository = repositories.getRequestRepository();
    final var requestQueueRepository = repositories.getRequestQueueRepository();

    final UpdateUponRequest updateUponRequest = new UpdateUponRequest(
      new UpdateItem(itemRepository, RequestQueueService.using(clients)),
      new UpdateLoan(clients, loanRepository, loanPolicyRepository),
        UpdateRequestQueue.using(clients, requestRepository,
          requestQueueRepository));

    final CreateRequestService createRequestService = new CreateRequestService(repositories,
      updateUponRequest, new RequestLoanValidator(itemFinder, loanRepository),
      new RequestNoticeSender(clients), regularRequestBlockValidators(clients), eventPublisher,
      new FailFastErrorHandler());

    return placeRequest(requestRepresentations, 0, createRequestService,
      clients, new ArrayList<>(), repositories);
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequest(
    List<JsonObject> itemRequests, int startIndex, CreateRequestService createRequestService,
    Clients clients, List<String> errors, RequestRelatedRepositories repositories) {

    log.debug("RequestByInstanceIdResource.placeRequest, startIndex={}, itemRequestSize={}",
      startIndex, itemRequests.size());

    if (startIndex >= itemRequests.size()) {
      String aggregateFailures = String.format("%n%s", String.join("%n", errors));

      return CompletableFuture.completedFuture(failedDueToServerError(
        "Failed to place a request for the instance. Reasons: " + aggregateFailures));
    }

    JsonObject currentItemRequest = itemRequests.get(startIndex);

    final RequestFromRepresentationService requestFromRepresentationService =
      new RequestFromRepresentationService(Request.Operation.CREATE, repositories,
        createProxyRelationshipValidator(currentItemRequest, clients),
        new ServicePointPickupLocationValidator(),
        new FailFastErrorHandler(),
        new ItemByInstanceIdFinder(clients.holdingsStorage(), repositories.getItemRepository()),
        ItemForTlrService.using(repositories));

    return requestFromRepresentationService.getRequestFrom(currentItemRequest)
      .thenCompose(r -> r.after(createRequestService::createRequest))
      .thenCompose(r -> {
          if (r.succeeded()) {
            log.debug("RequestByInstanceIdResource.placeRequest: succeeded creating request for item {}",
                currentItemRequest.getString(ITEM_ID));
            return CompletableFuture.completedFuture(r);
          } else {
            String reason = getErrorMessage(r.cause());
            errors.add(reason);

            log.debug("Failed to create request for item {} with reason: {}", currentItemRequest.getString(ITEM_ID), reason);
            return placeRequest(itemRequests, startIndex +1,
              createRequestService, clients, errors, repositories);
          }
        });
  }

  public static Result<InstanceRequestRelatedRecords> rankItemsByMatchingServicePoint(
    InstanceRequestRelatedRecords records) {

    final Collection<Item> unsortedAvailableItems = records.getUnsortedAvailableItems();
    final UUID pickupServicePointId = records.getInstanceLevelRequest().getPickupServicePointId();

    return of(() -> {

      Map<Boolean, List<Item>> itemsPartitionedByLocationServedByPickupPoint = unsortedAvailableItems.stream()
        .collect(Collectors.partitioningBy(i -> Optional.ofNullable(i.getLocation())
          .map(location -> location.homeLocationIsServedBy(pickupServicePointId))
          .orElse(false)));

      final List<Item> rankedItems = new LinkedList<>();

      //Compose the final list of Items with the matchingItems (items that has matching service pointID) on top.
      rankedItems.addAll(itemsPartitionedByLocationServedByPickupPoint.get(true));
      rankedItems.addAll(itemsPartitionedByLocationServedByPickupPoint.get(false));

      records.setSortedAvailableItems(rankedItems);

      return records;
    });
  }

  static Result<List<JsonObject>> instanceToItemRequests(
    InstanceRequestRelatedRecords requestRecords) {

    final RequestByInstanceIdRequest requestByInstanceIdRequest = requestRecords.getInstanceLevelRequest();
    final List<Item> combinedItems = requestRecords.getCombinedSortedItemsList();

    if (combinedItems == null || combinedItems.isEmpty()) {
      return failedValidation("Cannot create request objects when items list is null or empty", "items", "null");    }

    LinkedList<JsonObject> requests = new LinkedList<>();

    for (Item item : combinedItems) {
      for (RequestType requestType : ORDERED_REQUEST_TYPES) {
        JsonObject requestBody = new JsonObject();

        write(requestBody, ITEM_ID, item.getItemId());
        write(requestBody, "requestDate",
          requestByInstanceIdRequest.getRequestDate());
        write(requestBody, REQUESTER_ID, requestByInstanceIdRequest.getRequesterId().toString());
        write(requestBody, "pickupServicePointId",
          requestByInstanceIdRequest.getPickupServicePointId().toString());
        write(requestBody, "fulfilmentPreference", DEFAULT_FULFILMENT_PREFERENCE.getValue());
        write(requestBody, "requestType", requestType.getValue());
        if (requestByInstanceIdRequest.getRequestExpirationDate() != null) {
          write(requestBody, "requestExpirationDate",
            requestByInstanceIdRequest.getRequestExpirationDate());
        }
        write(requestBody, "patronComments", requestByInstanceIdRequest.getPatronComments());
        write(requestBody, "instanceId", requestByInstanceIdRequest.getInstanceId());
        write(requestBody, "holdingsRecordId", item.getHoldingsRecordId());
        write(requestBody, "requestLevel", requestByInstanceIdRequest.getRequestLevel());
        requests.add(requestBody);
      }
    }
    return succeeded(requests);
  }

  private Result<InstanceRequestRelatedRecords> segregateItemsList(
    InstanceRequestRelatedRecords requestRelatedRecords ){

    Collection<Item> items = requestRelatedRecords.getAllUnsortedItems();

    log.debug("RequestByInstanceIdResource.segregateItemsList: Found {} items", items.size());

    Map<Boolean, List<Item>> partitions = items.stream()
      .collect(Collectors.partitioningBy(Item::isAvailable));

    requestRelatedRecords.setUnsortedAvailableItems(partitions.get(true));
    requestRelatedRecords.setUnsortedUnavailableItems(partitions.get(false));

    return succeeded(requestRelatedRecords);
  }

  private Result<InstanceRequestRelatedRecords> combineWithUnavailableItems(
    Map<Item, ZonedDateTime> itemDueDateMap,
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

        ArrayList<Item> finalOrderedList = new ArrayList<>(sortedMap.keySet());

        //put the items that it wasn't able to retrieve loans or RequestQueues for on the bottom of the list
        if (records.getItemsWithoutLoans() != null)
          finalOrderedList.addAll(records.getItemsWithoutLoans());
        if (records.getItemsWithoutRequests() != null)
          finalOrderedList.addAll(records.getItemsWithoutRequests());
        records.setSortedUnavailableItems(finalOrderedList);
      return records;
    });
  }

  private Result<Collection<Item>> validateRequester(Map<Item, RequestQueue> itemRequestQueueMap,
    InstanceRequestRelatedRecords requestPackage) {

    if (!itemRequestQueueMap.isEmpty()) {
      Collection<RequestQueue> requestQueues = itemRequestQueueMap.values();
      String requesterId = requestPackage.getInstanceLevelRequest().getRequesterId().toString();

      for (RequestQueue queue : requestQueues) {
        final Optional<Request> matchingRequest = queue.getRequests()
          .stream()
          .filter(request -> request.isOpen()
                           && Objects.equals(request.getUserId(), requesterId)
          ).findFirst();

        if (matchingRequest.isPresent()) {
            Map<String, String> parameters = new HashMap<>();
            parameters.put(REQUESTER_ID, requesterId);
            parameters.put(ITEM_ID, matchingRequest.get().getItemId());
            parameters.put("instanceId", requestPackage.getInstanceLevelRequest().getInstanceId().toString());
            String message = "This requester already has an open request for an item of this instance";
            return failedValidation(new ValidationError(message, parameters));
          }
        }
      }
    return of(itemRequestQueueMap::keySet);
  }

  private Result<Collection<Item>> validateItems(Collection<Item> items) {
    if (items == null || items.isEmpty()) {
      return failedValidation("There are no items for this instance", "items", "empty");
    }
    return succeeded(items);
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
