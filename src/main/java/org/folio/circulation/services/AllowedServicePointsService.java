package org.folio.circulation.services;

import static java.lang.String.format;
import static java.util.function.UnaryOperator.identity;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.asJson;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.AllowedServicePoint;
import org.folio.circulation.domain.AllowedServicePointsRequest;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class AllowedServicePointsService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final ServicePointRepository servicePointRepository;
  private final ItemByInstanceIdFinder itemFinder;

  public AllowedServicePointsService(Clients clients) {
    itemRepository = new ItemRepository(clients);
    userRepository = new UserRepository(clients);
    requestPolicyRepository = new RequestPolicyRepository(clients);
    servicePointRepository = new ServicePointRepository(clients);
    itemFinder = new ItemByInstanceIdFinder(clients.holdingsStorage(), itemRepository);
  }

  public CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  getAllowedServicePoints(AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePoints:: parameters request: {}", request);

    return userRepository.getUser(request.getRequesterId())
      .thenApply(r -> r.next(user -> refuseIfUserIsNotFound(request, user)))
      .thenCompose(r -> r.after(user -> getAllowedServicePoints(request, user)));
  }

  private Result<User> refuseIfUserIsNotFound(
    AllowedServicePointsRequest request, User user) {

    if (user == null) {
      log.error("refuseIfUserIsNotFound:: user is null");
      return failed(new ValidationErrorFailure(new ValidationError(
        format("User with id=%s cannot be found", request.getRequesterId()))));
    }

    return succeeded(user);
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  getAllowedServicePoints(AllowedServicePointsRequest request, User user) {

    log.debug("getAllowedServicePoints:: parameters request: {}, user: {}",
      request, user);

    return fetchItemsAndLookupRequestPolicies(request, user)
      .thenCompose(r -> r.after(policies -> allOf(policies, this::extractAllowedServicePoints)))
      .thenApply(r -> r.map(this::combineAllowedServicePoints));
  }

  private CompletableFuture<Result<Set<RequestPolicy>>> fetchItemsAndLookupRequestPolicies(
    AllowedServicePointsRequest request, User user) {

    log.debug("fetchItemsAndLookupRequestPolicies:: parameters request: {}, user: {}",
      request, user);

    return fetchItems(request)
      .thenCompose(r -> r.after(items -> requestPolicyRepository.lookupRequestPolicies(items, user)))
      .thenApply(r -> r.map(HashSet::new));
  }

  private CompletableFuture<Result<Collection<Item>>> fetchItems(
    AllowedServicePointsRequest request) {

    return request.getItemId() != null
      ? fetchItemForItemLevel(request)
      : fetchItemsForTitleLevel(request);
  }

  private CompletableFuture<Result<Collection<Item>>> fetchItemsForTitleLevel(
    AllowedServicePointsRequest request) {
    return itemFinder.getItemsByInstanceId(UUID.fromString(request.getInstanceId()), true);
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
  extractAllowedServicePoints(RequestPolicy requestPolicy) {

    log.debug("extractAllowedServicePoints:: parameters requestPolicy: {}", requestPolicy);

    List<RequestType> allowedTypes = Arrays.stream(RequestType.values())
      .filter(requestPolicy::allowsType)
      .toList();

    if (allowedTypes.isEmpty()) {
      log.info("fetchAllowedServicePoints:: allowedTypes is empty");
      return ofAsync(new EnumMap<>(RequestType.class));
    }

    var allowedServicePointsInPolicy = requestPolicy.getAllowedServicePoints();
    if (allowedServicePointsInPolicy.isEmpty()) {
      log.info("extractAllowedServicePoints:: allowedServicePointsInPolicy is empty");

      return fetchAllowedServicePoints()
        .thenApply(r -> r.map(allowedServicePoints -> groupAllowedServicePointsByRequestType(
          allowedTypes, allowedServicePoints)));
    }

    Set<String> allowedServicePointsIds = allowedServicePointsInPolicy.values().stream()
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());

    return fetchPickupLocationServicePointsByIds(allowedServicePointsIds)
      .thenApply(r -> r.map(servicePoints -> groupAllowedServicePointsByRequestType(
        allowedTypes, servicePoints, allowedServicePointsInPolicy)));
  }

  private Map<RequestType, Set<AllowedServicePoint>> groupAllowedServicePointsByRequestType(
    List<RequestType> allowedTypes, Set<AllowedServicePoint> allowedServicePoints) {

    log.debug("groupAllowedServicePointsByRequestType:: parameters allowedTypes: {}, " +
        "servicePointsIds: {}", () -> asJson(allowedTypes), () -> asJson(allowedServicePoints));

    if (allowedServicePoints.isEmpty()) {
      return new EnumMap<>(RequestType.class);
    }

    return allowedTypes.stream()
      .collect(Collectors.toMap(identity(), type -> allowedServicePoints));
  }

  private Map<RequestType, Set<AllowedServicePoint>> groupAllowedServicePointsByRequestType(
    List<RequestType> allowedTypes, Set<AllowedServicePoint> allowedServicePoints,
    Map<RequestType, Set<String>> allowedServicePointsInPolicy) {

    log.debug("groupAllowedServicePointsByRequestType:: parameters allowedTypes: {}, " +
        "servicePointsIds: {}, allowedServicePointsInPolicy: {}", () -> asJson(allowedTypes),
      () -> asJson(allowedServicePoints), () -> allowedServicePointsInPolicy);

    Map<RequestType, Set<AllowedServicePoint>> groupedAllowedServicePoints =
      new EnumMap<>(RequestType.class);
    for (Map.Entry<RequestType, Set<String>> entry : allowedServicePointsInPolicy.entrySet()) {
      RequestType requestType = entry.getKey();
      Set<String> servicePointIds = entry.getValue();

      if (allowedTypes.contains(requestType) && !servicePointIds.isEmpty()) {
        Set<AllowedServicePoint> allowedServicePointsForType = allowedServicePoints.stream()
          .filter(allowedServicePoint -> servicePointIds.contains(allowedServicePoint.getId()))
          .collect(Collectors.toSet());

        if (!allowedServicePointsForType.isEmpty()) {
          groupedAllowedServicePoints.put(requestType, allowedServicePointsForType);
        }
      }
    }

    return groupedAllowedServicePoints;
  }

  private Map<RequestType, Set<AllowedServicePoint>> combineAllowedServicePoints(
    List<Map<RequestType, Set<AllowedServicePoint>>> maps) {

    return maps.stream()
      .flatMap(map -> map.entrySet().stream())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (set1, set2) -> {
        set1.addAll(set2);
        return set1;
      }, () -> new EnumMap<>(RequestType.class)));
  }

  public CompletableFuture<Result<Set<AllowedServicePoint>>> fetchAllowedServicePoints() {
    return servicePointRepository.fetchPickupLocationServicePoints()
      .thenApply(r -> r.map(servicePoints -> servicePoints.stream()
        .map(AllowedServicePoint::new)
        .collect(Collectors.toSet())));
  }

  public CompletableFuture<Result<Set<AllowedServicePoint>>> fetchPickupLocationServicePointsByIds(
    Set<String> ids) {

    log.debug("filterIdsByServicePointsAndPickupLocationExistence:: parameters ids: {}",
      () -> collectionAsString(ids));

    return servicePointRepository.fetchPickupLocationServicePointsByIds(ids)
      .thenApply(servicePointsResult -> servicePointsResult
        .map(servicePoints -> servicePoints.stream()
          .map(AllowedServicePoint::new)
          .collect(Collectors.toSet())));
  }
}
