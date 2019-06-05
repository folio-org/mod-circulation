package org.folio.circulation.resources;

import static org.folio.circulation.domain.representations.RequestProperties.PROXY_USER_ID;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.CreateRequestService;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LocationRepository;
import org.folio.circulation.domain.RequestAndRelatedRecords;
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
import org.folio.circulation.support.Pair;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestByInstanceIdResource extends Resource {
  private UserRepository userRepository;
  private Clients clients;
  private LocationRepository locationRepository;
  private LoanRepository loanRepository;
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

    clients = Clients.create(context, client);
    locationRepository = new LocationRepository(clients);
    userRepository = new UserRepository(clients);
    loanRepository = new LoanRepository(clients);
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

    ItemByInstanceIdFinder finder = new ItemByInstanceIdFinder(clients.holdingsStorage(),
      clients.itemsStorage(), itemRepository);

    String pickupServicePointId = requestByInstanceIdRequest.getPickupServicePointId().toString();
    final CompletableFuture<Result<Collection<Item>>> items = finder.getItemsByInstanceId(requestByInstanceIdRequest.getInstanceId().toString());

    final CompletableFuture<Result<Collection<Item>>> availableItems = items.thenApply(r -> r.next(this::getfilteredAvailableItems));

    availableItems.thenCompose(r -> r.after( collectionResult -> findItemsWithMatchingServicePointId(
                                                                  pickupServicePointId, collectionResult, locationRepository)))
      .thenApply( r -> r.next( itemsFound -> instanceToItemRequests(requestByInstanceIdRequest, itemsFound)))
      .thenCompose( r -> r.after( requests -> placeRequests(requests, clients)))
      .thenApply(r -> r.map(RequestAndRelatedRecords::getRequest))
      .thenApply(r -> r.map(new RequestRepresentation()::extendedRepresentation))
      .thenApply(CreatedJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()))
      .exceptionally( err -> {
          log.error("Error processing title-level request", err);
          return null;});
  }


  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequests(List<JsonObject> itemRequestRepresentations, Clients clients) {

    final RequestNoticeSender requestNoticeSender = RequestNoticeSender.using(clients);

    final CreateRequestService createRequestService = new CreateRequestService(
      RequestRepository.using(clients),
      new UpdateItem(clients),
      new UpdateLoanActionHistory(clients),
      new UpdateLoan(clients, loanRepository, new LoanPolicyRepository(clients)),
      new RequestPolicyRepository(clients),
      loanRepository, requestNoticeSender);

    return placeRequest(itemRequestRepresentations, 0, createRequestService);
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequest(List<JsonObject> itemRequests, int startIndex,
                                                              CreateRequestService createRequestService) {
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
            log.debug("Failed to create request for " + currentItemRequest.getString("id"));
            return placeRequest(itemRequests, startIndex +1, createRequestService);
          }
        });
  }


  private static CompletableFuture<Result<Collection<Item>>> findItemsWithMatchingServicePointId(String pickupServicePointId,
                                                                                                 Collection<Item> items,
                                                                                                 LocationRepository locationRepository) {

    LinkedList<Pair>  locationIdItemMap = new LinkedList<>();

    return getLocationFutures(items, locationRepository, locationIdItemMap)
      .thenApply(locations -> {
        List<Item> matchingItemsList = getItemsWithMatchingServicePointIds(items, pickupServicePointId);

        return succeeded(getOrderedAvailableItemsList(matchingItemsList, locationIdItemMap));
      });
  }

  /**
   * Method to get the CompletableFutures of locations. It also builds a locationIdItemMap to later
   * easily retrieves the item by a locationId. This map is passed back implicitly.
   * @param items items to get current locations
   * @param locationRepository location repository to get location objects
   * @param locationIdItemMap a simple non-unique KVP map between locationId and Item.
   * @return Futures promising Location results
   */
  public static CompletableFuture<Collection<Result<JsonObject>>> getLocationFutures(Collection<Item> items,
                                                                                     LocationRepository locationRepository,
                                                                                     LinkedList<Pair> locationIdItemMap) {
    //for a given location ID, find all the service points.
    //if there's a matching service point ID, pick that item
    Collection<CompletableFuture<Result<JsonObject>>> locationFutures = new ArrayList<>();
    //Find locations of all items
    for (Item item : items) {
      Pair kvp = new Pair(item.getLocationId(), item);
      locationIdItemMap.add(kvp);
      locationFutures.add(
        locationRepository.getLocation(item)
      );
    }

    //Collect the location objects once they come back
    CompletableFuture<Void> allFutures = CompletableFuture.allOf(
      locationFutures.toArray(
        new CompletableFuture[locationFutures.size()]));

    return allFutures.thenApply(x -> locationFutures.stream()
      .map(CompletableFuture::join)
      .collect(Collectors.toList()));
  }

  public static List<Item> getOrderedAvailableItemsList(List<Item> matchingItemsList, LinkedList<Pair> locationIdItemMap) {

    //Compose the final list of Items with the matchingItems (items that has matching service pointID) on top.
    List<Item> finalOrderedList = new LinkedList<>();
    finalOrderedList.addAll(matchingItemsList);

    //loop through all the items in the maps and add the remaining ones, those that have not been added are added to the bottom.
    Collection<Item> locationItemMapValues = locationIdItemMap.stream()
                                                              .map(Pair::getValue)
                                                              .collect(Collectors.toList());
    for (Item anItem : locationItemMapValues) {
      if (!matchingItemsList.contains(anItem)) {
        finalOrderedList.add(anItem);
      }
    }

    return finalOrderedList;
  }

  /**
   * Method that is responsible for identifying items at given location(s) each with servicePointId that matches the pickupServicePointId
   * @param items set of items to filter
   * @param pickupServicePointId servicePointId to find available items at.
   * @return  A list of items at a location where each has a service point that matches the pickupServicePointId.
   */
  public static List<Item> getItemsWithMatchingServicePointIds(Collection<Item> items, String pickupServicePointId) {

    return items.stream()
      .filter(item -> item.homeLocationIsServedBy(UUID.fromString(pickupServicePointId)))
      .collect(Collectors.toList());
  }

  public static Result<LinkedList<JsonObject>> instanceToItemRequests(RequestByInstanceIdRequest requestByInstanceIdRequest, Collection<Item> items) {

    if (items == null || items.isEmpty()) {
      return failedValidation("Cannot create request objects when items list is null or empty", "items", "null");    }

    RequestType[] types = RequestType.values();
    LinkedList<JsonObject> requests = new LinkedList<>();

    final String defaultFulfilmentPreference = "Hold Shelf";

    for (Item item: items) {
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

  private Result<Collection<Item>> getfilteredAvailableItems(Collection<Item> items) {

    if (items == null ||items.isEmpty()) {
      return failedValidation("Items list is null or empty", "items", "null");
    }

    return succeeded(items.stream()
      .filter(Item::isAvailable)
      .collect(Collectors.toList()));
  }

  private ProxyRelationshipValidator createProxyRelationshipValidator(
    JsonObject representation,
    Clients clients) {

    return new ProxyRelationshipValidator(clients, () ->
      singleValidationError("proxyUserId is not valid",
        PROXY_USER_ID, representation.getString(PROXY_USER_ID)));
  }
}
