package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.asJson;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.AllowedServicePointsRequest;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RecordNotFoundFailure;
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
      .thenCompose(r -> r.after(user -> lookupRequestPolicy(request, user)))
      .thenCompose(r -> r.after(this::extractAllowedServicePoints));
  }

  private CompletableFuture<Result<RequestPolicy>> lookupRequestPolicy(
    AllowedServicePointsRequest request, User user) {

    log.debug("lookupRequestPolicy:: parameters: request={}, user={}", request, user);

    if (user == null) {
      log.error("lookupRequestPolicy:: user is null");
      return completedFuture(failed(new RecordNotFoundFailure("User", request.getRequesterId())));
    }

    return itemRepository.fetchById(request.getItemId())
      .thenCompose(r -> r.after(item -> requestPolicyRepository.lookupRequestPolicy(item, user)));
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
      .map(UUID::toString)
      .collect(Collectors.toSet());

    return servicePointRepository.filterIdsByServicePointsAndPickupLocationExistence(
      allowedServicePointsIdsSet)
        .thenApply(r -> r.next(filteredSpIds -> includeOnlyExistingServicePoints(
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
    return servicePointRepository.fetchPickupLocationServicePointsIds()
      .thenCompose(r -> r.after(servicePointIds -> {
        Map<RequestType, Set<String>> allowedServicePoints = new EnumMap<>(RequestType.class);
        allowedTypes.forEach(requestType -> allowedServicePoints.put(requestType, servicePointIds));
        log.info("fetchAllowedServicePoints:: result: {}", () -> asJson(allowedServicePoints));

        return ofAsync(allowedServicePoints);
      }));

  }

  private Result<Map<RequestType, Set<String>>> includeOnlyExistingServicePoints(
    Map<RequestType, Set<UUID>> allowedServicePointsInPolicy, Set<String> relevantSpIds,
    RequestPolicy requestPolicy) {

    log.debug("includeOnlyExistingServicePoints:: parameters: allowedServicePointsInPolicy={}, " +
      "relevantSpIds={}, requestPolicy={}", () -> asJson(allowedServicePointsInPolicy),
      () -> asJson(relevantSpIds), () -> asJson(requestPolicy));

    final Map<RequestType, Set<String>> allowedServicePoints = new EnumMap<>(RequestType.class);
    allowedServicePointsInPolicy.forEach((requestType, uuidSet) -> {
      if (requestPolicy.allowsType(requestType)) {
        allowedServicePoints.put(requestType,
          uuidSet.stream()
            .map(UUID::toString)
            .filter(relevantSpIds::contains)
            .collect(Collectors.toSet()));
      }
    });

    return succeeded(allowedServicePoints);
  }

  private CompletableFuture<Result<Map<RequestType, Set<String>>>> getAllowedServicePointsForInstance(
    AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePointsForInstance:: parameters: request={}", request);

    return ofAsync(new EnumMap<>(RequestType.class));
  }

}
