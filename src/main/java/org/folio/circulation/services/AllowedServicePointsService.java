package org.folio.circulation.services;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
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

  public CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>> getAllowedServicePoints(
    AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePoints:: parameters: request={}", request);

    return request.getItemId() != null
      ? getAllowedServicePointsForItem(request)
      : getAllowedServicePointsForInstance(request);
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  getAllowedServicePointsForItem(AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePointsForItem:: parameters: request={}", request);

    return userRepository.getUser(request.getRequesterId())
      .thenCompose(r -> r.after(user -> fetchItemAndLookupRequestPolicy(request, user)))
      .thenCompose(r -> r.after(this::extractAllowedServicePoints));
  }

  private CompletableFuture<Result<RequestPolicy>> fetchItemAndLookupRequestPolicy(
    AllowedServicePointsRequest request, User user) {

    log.debug("lookupRequestPolicy:: parameters: request={}, user={}", request, user);

    if (user == null) {
      log.error("lookupRequestPolicy:: user is null");
      return completedFuture(failed(new ValidationErrorFailure(new ValidationError(
        format("User with id=%s cannot be found", request.getRequesterId())))));
    }

    return itemRepository.fetchById(request.getItemId())
      .thenCompose(r -> r.after(item -> lookupRequestPolicy(item, user, request)));
  }

  private CompletableFuture<Result<Set<RequestPolicy>>> fetchItemsByInstanceAndLookupRequestPolicies(
    AllowedServicePointsRequest request, User user) {

    log.debug("lookupRequestPolicy:: parameters: request={}, user={}", request, user);

    if (user == null) {
      log.error("lookupRequestPolicy:: user is null");
      return completedFuture(failed(new ValidationErrorFailure(new ValidationError(
        format("User with id=%s cannot be found", request.getRequesterId())))));
    }

    return itemFinder.getItemsByInstanceId(UUID.fromString(request.getInstanceId()), true)
      .thenCompose(r -> r.after(items -> requestPolicyRepository.lookupRequestPolicies(items, user)))
      .thenApply(r -> r.map(HashSet::new));
  }

  private CompletableFuture<Result<RequestPolicy>> lookupRequestPolicy(Item item, User user,
    AllowedServicePointsRequest request) {

    log.debug("lookupRequestPolicy:: parameters item: {}, user: {}", item, user);

    if (item.isNotFound()) {
      log.error("lookupRequestPolicy:: item is null");
      return completedFuture(failed(new ValidationErrorFailure(new ValidationError(
        format("Item with id=%s cannot be found", request.getItemId())))));
    }

    return requestPolicyRepository.lookupRequestPolicy(item, user);
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  extractAllowedServicePoints(RequestPolicy requestPolicy) {

    log.debug("extractAllowedServicePoints:: parameters: requestPolicy={}", requestPolicy);

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

      return fetchAllowedServicePoints(allowedTypes);
    }

    Set<String> allowedServicePointsIdsSet = allowedServicePointsInPolicy.values().stream()
      .flatMap(Collection::stream)
      .map(UUID::toString)
      .collect(Collectors.toSet());

    return fetchPickupLocationServicePointsByIds(allowedServicePointsIdsSet)
      .thenApply(r -> r.map(servicePoints -> groupAllowedServicePointsByRequestType(
        allowedTypes, servicePoints)));
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  fetchAllowedServicePoints(List<RequestType> allowedTypes) {

    log.debug("fetchAllowedServicePoints:: parameters: allowedTypes={}",
      () -> asJson(allowedTypes));

    return fetchAllowedServicePoints()
      .thenApply(r -> r.map(allowedServicePoints -> groupAllowedServicePointsByRequestType(
        allowedTypes, allowedServicePoints)));
  }

  private Map<RequestType, Set<AllowedServicePoint>> groupAllowedServicePointsByRequestType(
    List<RequestType> allowedTypes, Set<AllowedServicePoint> allowedServicePoints) {

    log.debug("groupAllowedServicePointsByRequestType:: parameters: allowedTypes={}, " +
        "servicePointsIds={}", () -> asJson(allowedTypes), () -> asJson(allowedServicePoints));

    Map<RequestType, Set<AllowedServicePoint>> groupedAllowedServicePoints =
      new EnumMap<>(RequestType.class);
    allowedTypes.forEach(requestType -> {
      if (!allowedServicePoints.isEmpty()) {
        groupedAllowedServicePoints.put(requestType, allowedServicePoints);
      }
    });
    log.info("groupAllowedServicePointsByRequestType:: result: {}",
      () -> asJson(allowedServicePoints));

    return groupedAllowedServicePoints;
  }

  private CompletableFuture<Result<Map<RequestType, Set<AllowedServicePoint>>>>
  getAllowedServicePointsForInstance(AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePointsForInstance:: parameters: request={}", request);

    return userRepository.getUser(request.getRequesterId())
      .thenCompose(r -> r.after(user -> fetchItemsByInstanceAndLookupRequestPolicies(request, user)))
      .thenCompose(r -> r.after(policies -> allOf(policies, this::extractAllowedServicePoints)))
      .thenApply(r -> r.map(this::combineAllowedServicePoints));
  }

  private Map<RequestType, Set<AllowedServicePoint>> combineAllowedServicePoints(
    List<Map<RequestType, Set<AllowedServicePoint>>> maps) {

    return maps.stream()
      .flatMap(map -> map.entrySet().stream())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (set1, set2) -> {
        Set<AllowedServicePoint> mergedSet = new HashSet<>(set1);
        mergedSet.addAll(set2);
        return mergedSet;
      }, () -> new EnumMap<>(RequestType.class)));
  }

  public CompletableFuture<Result<Set<AllowedServicePoint>>> fetchAllowedServicePoints() {
    return servicePointRepository.fetchPickupLocationServicePoints()
      .thenApply(servicePointsResult -> servicePointsResult
        .map(servicePoints -> servicePoints.stream()
          .map(servicePoint -> new AllowedServicePoint(servicePoint.getId(),
            servicePoint.getName()))
          .collect(Collectors.toSet())));
  }

  public CompletableFuture<Result<Set<AllowedServicePoint>>> fetchPickupLocationServicePointsByIds(
    Set<String> ids) {

    log.debug("filterIdsByServicePointsAndPickupLocationExistence:: parameters ids: {}",
      () -> collectionAsString(ids));

    return servicePointRepository.fetchPickupLocationServicePointsByIds(ids)
      .thenApply(servicePointsResult -> servicePointsResult
        .map(servicePoints -> servicePoints.stream()
          .map(servicePoint -> new AllowedServicePoint(servicePoint.getId(),
            servicePoint.getName()))
          .collect(Collectors.toSet())));
  }
}
