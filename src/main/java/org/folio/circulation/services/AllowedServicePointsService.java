package org.folio.circulation.services;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.utils.LogUtil.asJson;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.AllowedServicePointsRequest;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
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

  public AllowedServicePointsService(Clients clients) {
    itemRepository = new ItemRepository(clients);
    userRepository = new UserRepository(clients);
    requestPolicyRepository = new RequestPolicyRepository(clients);
    servicePointRepository = new ServicePointRepository(clients);
  }

  public CompletableFuture<Result<Map<RequestType, Set<String>>>> getAllowedServicePoints(
    AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePoints:: parameters: request={}", request);

    return request.getItemId() != null
      ? getAllowedServicePointsForItem(request)
      : getAllowedServicePointsForInstance(request);
  }

  private CompletableFuture<Result<Map<RequestType, Set<String>>>> getAllowedServicePointsForItem(
    AllowedServicePointsRequest request) {

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
  private CompletableFuture<Result<Map<RequestType, Set<String>>>> extractAllowedServicePoints(
    RequestPolicy requestPolicy) {

    log.debug("extractAllowedServicePoints:: parameters: requestPolicy={}", requestPolicy);

    var allowedServicePointsInPolicy = requestPolicy.getAllowedServicePoints();
    if (allowedServicePointsInPolicy.isEmpty()) {
      log.info("extractAllowedServicePoints:: allowedServicePointsInPolicy is empty");

      return fetchAllowedServicePoints(requestPolicy);
    }

    Set<String> allowedServicePointsIdsSet = allowedServicePointsInPolicy.values().stream()
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());

    return filterIdsByServicePointsAndPickupLocationExistence(allowedServicePointsIdsSet)
      .thenApply(r -> r.map(filteredSpIds -> includeOnlyExistingServicePoints(
        allowedServicePointsInPolicy, filteredSpIds, requestPolicy)));
  }

  private CompletableFuture<Result<Map<RequestType, Set<String>>>> fetchAllowedServicePoints(
    RequestPolicy requestPolicy) {

    log.debug("fetchAllowedServicePoints:: parameters: requestPolicy={}", requestPolicy);

    List<RequestType> allowedTypes = Arrays.stream(RequestType.values())
      .filter(requestPolicy::allowsType)
      .toList();
    if (allowedTypes.isEmpty()) {
      log.info("fetchAllowedServicePoints:: allowedTypes is empty");

      return ofAsync(new EnumMap<>(RequestType.class));
    }
    log.info("fetchAllowedServicePoints:: allowedTypes={}", allowedTypes);

    return fetchPickupLocationServicePointsIds()
      .thenApply(r -> r.map(servicePointIds -> mapToAllowedServicePoints(allowedTypes,
        servicePointIds)));
  }

  private Map<RequestType, Set<String>> mapToAllowedServicePoints(List<RequestType> allowedTypes,
    Set<String> servicePointIds) {

    log.debug("mapToAllowedServicePoints:: parameters: allowedTypes={}, servicePointsIds={}",
      () -> asJson(allowedTypes), () -> asJson(servicePointIds));

    Map<RequestType, Set<String>> allowedServicePoints = new EnumMap<>(RequestType.class);
    allowedTypes.forEach(requestType -> allowedServicePoints.put(requestType, servicePointIds));
    log.info("fetchAllowedServicePoints:: result: {}", () -> asJson(allowedServicePoints));

    return allowedServicePoints;
  }

  private Map<RequestType, Set<String>> includeOnlyExistingServicePoints(
    Map<RequestType, Set<String>> allowedServicePointsInPolicy, Set<String> relevantSpIds,
    RequestPolicy requestPolicy) {

    log.debug("includeOnlyExistingServicePoints:: parameters: allowedServicePointsInPolicy={}, " +
      "relevantSpIds={}, requestPolicy={}", () -> asJson(allowedServicePointsInPolicy),
      () -> asJson(relevantSpIds), () -> asJson(requestPolicy));

    final Map<RequestType, Set<String>> allowedServicePoints = new EnumMap<>(RequestType.class);
    allowedServicePointsInPolicy.forEach((requestType, uuidSet) -> {
      if (requestPolicy.allowsType(requestType)) {
        log.info("includeOnlyExistingServicePoints:: requestType={} is allowed",
          requestType.getValue());
        Set<String> filteredIds = uuidSet.stream()
          .filter(relevantSpIds::contains)
          .collect(Collectors.toSet());
        if (!filteredIds.isEmpty()) {
          allowedServicePoints.put(requestType, filteredIds);
        }
      }
    });

    return allowedServicePoints;
  }

  private CompletableFuture<Result<Map<RequestType, Set<String>>>> getAllowedServicePointsForInstance(
    AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePointsForInstance:: parameters: request={}", request);

    return ofAsync(new EnumMap<>(RequestType.class));
  }

  public CompletableFuture<Result<Set<String>>> fetchPickupLocationServicePointsIds() {
    return servicePointRepository.fetchPickupLocationServicePoints()
      .thenApply(servicePointsResult -> servicePointsResult
        .map(servicePoints -> servicePoints.stream()
          .map(ServicePoint::getId)
          .collect(Collectors.toSet())));
  }

  public CompletableFuture<Result<Set<String>>> filterIdsByServicePointsAndPickupLocationExistence(
    Set<String> ids) {

    log.debug("filterIdsByServicePointsAndPickupLocationExistence:: parameters ids: {}",
      () -> collectionAsString(ids));

    return servicePointRepository.fetchServicePointsByIdsWithPickupLocation(ids)
      .thenApply(servicePointsResult -> servicePointsResult
        .map(servicePoints -> servicePoints.stream()
          .map(ServicePoint::getId)
          .collect(Collectors.toSet())));
  }
}
