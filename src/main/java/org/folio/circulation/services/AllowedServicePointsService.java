package org.folio.circulation.services;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.circulation.domain.Request.Operation.CREATE;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.PAGE;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.asJson;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.AllowedServicePoint;
import org.folio.circulation.domain.AllowedServicePointsRequest;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.RequestTypeItemStatusWhiteList;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.InstanceRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class AllowedServicePointsService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String ECS_REQUEST_ROUTING_INDEX_NAME = "ecsRequestRouting";
  private static final String PICKUP_LOCATION_INDEX_NAME = "pickupLocation";
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final RequestRepository requestRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final ServicePointRepository servicePointRepository;
  private final ItemByInstanceIdFinder itemFinder;
  private final SettingsRepository settingsRepository;
  private final InstanceRepository instanceRepository;
  private final String indexName;

  public AllowedServicePointsService(Clients clients, boolean isEcsRequestRouting) {
    itemRepository = new ItemRepository(clients);
    userRepository = new UserRepository(clients);
    requestRepository = new RequestRepository(clients);
    requestPolicyRepository = new RequestPolicyRepository(clients);
    servicePointRepository = new ServicePointRepository(clients, isEcsRequestRouting);
    settingsRepository = new SettingsRepository(clients);
    instanceRepository = new InstanceRepository(clients);
    itemFinder = new ItemByInstanceIdFinder(clients.holdingsStorage(), itemRepository);
    indexName = isEcsRequestRouting ? ECS_REQUEST_ROUTING_INDEX_NAME : PICKUP_LOCATION_INDEX_NAME;
  }

  public CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  getAllowedServicePoints(AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePoints:: parameters request: {}", request);

    return ofAsync(request)
      .thenCompose(r -> r.after(this::fetchInstance))
      .thenCompose(r -> r.after(this::fetchRequest))
      .thenCompose(r -> r.after(this::getPatronGroupId))
      .thenCompose(r -> r.after(patronGroupId -> getAllowedServicePoints(request,
        patronGroupId)));
  }

  private CompletableFuture<Result<AllowedServicePointsRequest>> fetchInstance(
    AllowedServicePointsRequest request) {

    final String instanceId = request.getInstanceId();
    if (instanceId == null) {
      log.info("fetchInstance:: instanceId is null, doing nothing");
      return ofAsync(request);
    }

    return instanceRepository.fetchById(instanceId)
      .thenApply(r -> r.failWhen(
        instance -> succeeded(instance.isNotFound()),
        instance -> notFoundValidationFailure(instanceId, Instance.class)))
      // no need to save fetched instance, we only want to make sure that it exists
      .thenApply(r -> r.map(ignored -> request));
  }

  private CompletableFuture<Result<AllowedServicePointsRequest>> fetchRequest(
    AllowedServicePointsRequest allowedServicePointsRequest) {

    String requestId = allowedServicePointsRequest.getRequestId();
    if (requestId == null) {
      log.info("fetchRequest:: requestId is null, doing nothing");
      return ofAsync(allowedServicePointsRequest);
    }

    log.info("fetchRequest:: requestId is not null, fetching request");

    return requestRepository.getByIdWithoutRelatedRecords(requestId)
      .thenApply(r -> r.mapFailure(failure ->  failed(failure instanceof RecordNotFoundFailure ?
        notFoundValidationFailure(requestId, Request.class) : failure)))
      .thenApply(r -> r.map(allowedServicePointsRequest::updateWithRequestInformation));
  }

  private CompletableFuture<Result<String>> getPatronGroupId(AllowedServicePointsRequest request) {

    if (request.getPatronGroupId() != null) {
      return ofAsync(request.getPatronGroupId());
    }

    final String userId = request.getRequesterId();

    return userRepository.getUser(userId)
      .thenApply(r -> r.failWhen(
        user -> succeeded(user == null),
        user -> notFoundValidationFailure(userId, User.class)))
      .thenApply(result -> result.map(User::getPatronGroupId));
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  getAllowedServicePoints(AllowedServicePointsRequest request, String patronGroupId) {

    log.debug("getAllowedServicePoints:: parameters request: {}, patronGroupId: {}", request, patronGroupId);

    return fetchItems(request)
      .thenCompose(r -> r.after(items -> getAllowedServicePoints(request, patronGroupId, items)));
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  getAllowedServicePoints(AllowedServicePointsRequest request, String patronGroupId,
    Collection<Item> items) {

    BiFunction<RequestPolicy, Set<Item>, CompletableFuture<Result<Map<RequestType,
      Set<AllowedServicePoint>>>>> mappingFunction = request.isImplyingItemStatusIgnore()
      ? this::extractAllowedServicePointsIgnoringItemStatus
      : this::extractAllowedServicePointsConsideringItemStatus;

    if (request.isUseStubItem()) {
      return requestPolicyRepository.lookupRequestPolicy(patronGroupId)
        .thenCompose(r -> r.after(policy -> extractAllowedServicePointsIgnoringItemStatus(
          policy, new HashSet<>())));
    }

    if (items.isEmpty() && request.isForTitleLevelRequest()) {
      log.info("getAllowedServicePoints:: requested instance has no items");
      return getAllowedServicePointsForTitleWithNoItems(request);
    }

    return requestPolicyRepository.lookupRequestPolicies(items, patronGroupId)
      .thenCompose(r -> r.after(policies -> allOf(policies, mappingFunction)))
      .thenApply(r -> r.map(this::combineAllowedServicePoints));
    // TODO: remove irrelevant request types for REPLACE
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  getAllowedServicePointsForTitleWithNoItems(AllowedServicePointsRequest request) {

    if (request.isForTitleLevelRequest() && request.getOperation() == CREATE) {
      log.info("getAllowedServicePointsForTitleWithNoItems:: checking TLR settings");
      return settingsRepository.lookupTlrSettings()
        .thenCompose(r -> r.after(this::considerTlrSettings));
    }

    log.info("getAllowedServicePointsForTitleWithNoItems:: no need to check TLR-settings");
    return ofAsync(emptyMap());
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>> considerTlrSettings(
    TlrSettingsConfiguration tlrSettings) {

    if (!tlrSettings.isTitleLevelRequestsFeatureEnabled() ||
      tlrSettings.isTlrHoldShouldFollowCirculationRules()) {

      log.info("considerTlrSettings:: TLR-settings are checked, doing nothing");
      return ofAsync(emptyMap());
    }

    log.info("considerTlrSettings:: allowing all pickup locations for Hold");

    return fetchAllowedServicePoints()
      .thenApply(r -> r.map(sp -> sp.isEmpty() ? emptyMap() : Map.of(HOLD, sp)));
  }

  private CompletableFuture<Result<Collection<Item>>> fetchItems(
    AllowedServicePointsRequest request) {

    return request.isForItemLevelRequest()
      ? fetchItemForItemLevel(request)
      : fetchItemsForTitleLevel(request);
  }

  private CompletableFuture<Result<Collection<Item>>> fetchItemsForTitleLevel(
    AllowedServicePointsRequest request) {
    return itemFinder.getItemsByInstanceId(UUID.fromString(request.getInstanceId()), false);
  }

  private CompletableFuture<Result<Collection<Item>>> fetchItemForItemLevel(
    AllowedServicePointsRequest request) {

    return itemRepository.fetchById(request.getItemId())
      .thenApply(r -> r.next(item -> refuseIfItemIsNotFound(item, request)))
      .thenApply(r -> r.map(List::of));
  }

  private Result<Item> refuseIfItemIsNotFound(Item item, AllowedServicePointsRequest request) {
    log.debug("refuseIfItemIsNotFound:: parameters item: {}, request: {}",
      item, request);

    if (item.isNotFound()) {
      log.error("refuseIfItemIsNotFound:: item is not found");
      return failed(new ValidationErrorFailure(new ValidationError(
        format("Item with id=%s cannot be found", request.getItemId()))));
    }

    return succeeded(item);
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  extractAllowedServicePointsIgnoringItemStatus(RequestPolicy requestPolicy, Set<Item> items) {

    return extractAllowedServicePoints(requestPolicy, items, true);
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  extractAllowedServicePointsConsideringItemStatus(RequestPolicy requestPolicy, Set<Item> items) {

    return extractAllowedServicePoints(requestPolicy, items, false);
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  extractAllowedServicePoints(RequestPolicy requestPolicy, Set<Item> items,
    boolean ignoreItemStatus) {

    log.debug("extractAllowedServicePoints:: parameters requestPolicy: {}, items: {}, " +
        "ignoreItemStatus: {}", requestPolicy, items, ignoreItemStatus);

    List<RequestType> requestTypesAllowedByPolicy = Arrays.stream(RequestType.values())
      .filter(requestPolicy::allowsType)
      .collect(Collectors.toCollection(ArrayList::new)); // collect into a mutable list

    if (requestTypesAllowedByPolicy.isEmpty()) {
      log.info("fetchAllowedServicePoints:: allowedTypes is empty");
      return ofAsync(new EnumMap<>(RequestType.class));
    }

    log.info("fetchAllowedServicePoints:: types allowed by policy: {}",
      requestTypesAllowedByPolicy);

    var servicePointAllowedByPolicy = requestPolicy.getAllowedServicePoints();

    List<RequestType> requestTypesAllowedByItemStatus = ignoreItemStatus
      ? List.of(PAGE, RECALL, HOLD)
      : items.stream()
      .map(Item::getStatus)
      .map(RequestTypeItemStatusWhiteList::getRequestTypesAllowedForItemStatus)
      .flatMap(Collection::stream)
      .distinct()
      .toList();

    log.info("fetchAllowedServicePoints:: types allowed by status: {}",
      requestTypesAllowedByItemStatus);

    List<RequestType> requestTypesAllowedByPolicyAndStatus = requestTypesAllowedByPolicy.stream()
      .filter(requestTypesAllowedByItemStatus::contains)
      .collect(Collectors.toCollection(ArrayList::new)); // collect into a mutable list

    // TODO: fetch service points on a later stage, we only need IDs here
    return fetchServicePoints(requestTypesAllowedByPolicy, servicePointAllowedByPolicy)
      .thenApply(r -> r.map(servicePoints -> groupAllowedServicePointsByRequestType(
        requestTypesAllowedByPolicyAndStatus, servicePoints, servicePointAllowedByPolicy)));
  }

  private CompletableFuture<Result<Set<AllowedServicePoint>>> fetchServicePoints(
    List<RequestType> requestTypesAllowedByPolicy,
    Map<RequestType, Set<String>> servicePointsAllowedByPolicy) {

    Set<String> allowedServicePointsIds = servicePointsAllowedByPolicy.values().stream()
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());

    return requestTypesAllowedByPolicy.size() == servicePointsAllowedByPolicy.size()
      ? fetchPickupLocationServicePointsByIds(allowedServicePointsIds)
      : fetchAllowedServicePoints();
  }

  private Map<RequestType, Set<AllowedServicePoint>> groupAllowedServicePointsByRequestType(
    List<RequestType> requestTypesAllowedByPolicyAndStatus,
    Set<AllowedServicePoint> fetchedServicePoints,
    Map<RequestType, Set<String>> servicePointAllowedByPolicy) {

    log.debug("groupAllowedServicePointsByRequestType:: parameters allowedTypes: {}, " +
        "servicePointsIds: {}, allowedServicePointsInPolicy: {}", () -> asJson(requestTypesAllowedByPolicyAndStatus),
      () -> asJson(fetchedServicePoints), () -> servicePointAllowedByPolicy);

    Map<RequestType, Set<AllowedServicePoint>> groupedAllowedServicePoints =
      new EnumMap<>(RequestType.class);
    for (Map.Entry<RequestType, Set<String>> entry : servicePointAllowedByPolicy.entrySet()) {
      RequestType requestType = entry.getKey();
      Set<String> servicePointIdsAllowedByPolicyForType = entry.getValue();

      if (requestTypesAllowedByPolicyAndStatus.contains(requestType) &&
        !servicePointIdsAllowedByPolicyForType.isEmpty()) {

        log.info("groupAllowedServicePointsByRequestType:: request type {} is allowed; " +
          "list of allowed SPs for type: {}", requestType, servicePointIdsAllowedByPolicyForType);

        Set<AllowedServicePoint> allowedAndExistingServicePointsForType =
          fetchedServicePoints.stream()
            .filter(allowedServicePoint ->
              servicePointIdsAllowedByPolicyForType.contains(allowedServicePoint.getId()))
            .collect(Collectors.toSet());

        if (allowedAndExistingServicePointsForType.isEmpty()) {
          log.info("groupAllowedServicePointsByRequestType:: allowed and existing SPs not found " +
            "for type {}", requestType);
          requestTypesAllowedByPolicyAndStatus.remove(requestType);
        } else {
          groupedAllowedServicePoints.put(requestType, allowedAndExistingServicePointsForType);
        }
      }
    }

    if (requestTypesAllowedByPolicyAndStatus.size() > groupedAllowedServicePoints.size()) {
      groupedAllowedServicePoints.putAll(
        requestTypesAllowedByPolicyAndStatus.stream()
          .filter(requestType -> !groupedAllowedServicePoints.containsKey(requestType))
          .collect(toMap(identity(), requestType -> fetchedServicePoints))
      );
    }

    log.info("groupAllowedServicePointsByRequestType:: result: {}", groupedAllowedServicePoints);

    return groupedAllowedServicePoints;
  }

  private Map<RequestType, Set<AllowedServicePoint>> combineAllowedServicePoints(
    List<Map<RequestType, Set<AllowedServicePoint>>> maps) {

    return maps.stream()
      .flatMap(map -> map.entrySet().stream())
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (set1, set2) -> {
        set1.addAll(set2);
        return set1;
      }, () -> new EnumMap<>(RequestType.class)));
  }

  private CompletableFuture<Result<Set<AllowedServicePoint>>> fetchAllowedServicePoints() {
    return servicePointRepository.fetchServicePointsByIndexName(indexName)
      .thenApply(r -> r.map(servicePoints -> servicePoints.stream()
        .map(AllowedServicePoint::new)
        .collect(Collectors.toSet())));
  }

  private CompletableFuture<Result<Set<AllowedServicePoint>>> fetchPickupLocationServicePointsByIds(
    Set<String> ids) {

    log.debug("filterIdsByServicePointsAndPickupLocationExistence:: parameters ids: {}",
      () -> collectionAsString(ids));

    return servicePointRepository.fetchPickupLocationServicePointsByIdsAndIndexName(ids, indexName)
      .thenApply(servicePointsResult -> servicePointsResult
        .map(servicePoints -> servicePoints.stream()
          .map(AllowedServicePoint::new)
          .collect(Collectors.toSet())));
  }

  private static <T> HttpFailure notFoundValidationFailure(String id, Class<T> type) {
    String errorMessage = String.format("%s with ID %s was not found", type.getSimpleName(), id);
    log.error("notFoundValidationFailure:: {}", errorMessage);

    return new ValidationErrorFailure(new ValidationError(errorMessage));
  }

}
