package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

public class InstanceRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient instancesClient;

  public InstanceRepository(Clients clients) {
    instancesClient = clients.instancesStorage();
  }

  public CompletableFuture<Result<Instance>> fetch(Request request) {
    return fetchById(request.getInstanceId());
  }

  private CompletableFuture<Result<Instance>> fetchById(String instanceId) {
    return SingleRecordFetcher.jsonOrNull(instancesClient, "instance")
      .fetch(instanceId)
      .thenApply(r -> r.map(Instance::from));
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findInstancesForRequests(MultipleRecords<Request> multipleRequests) {
    Collection<Request> requests = multipleRequests.getRecords();
    final List<String> instanceIdsToFetch = requests.stream()
      .filter(Objects::nonNull)
      .map(Request::getInstanceId)
      .filter(Objects::nonNull)
      .distinct()
      .collect(Collectors.toList());

    if (instanceIdsToFetch.isEmpty()) {
      log.info("No instance ids to query");
      return completedFuture(succeeded(multipleRequests));
    }

    return findWithMultipleCqlIndexValues(instancesClient, "instances", Instance::from)
      .findByIds(instanceIdsToFetch)
      .thenApply(multipleInstancesResult -> multipleInstancesResult.next(
        multipleInstances -> {
          List<Request> newRequestList = requests.stream()
            .map(getRequestMapper(multipleInstances))
            .collect(Collectors.toList());

          return succeeded(new MultipleRecords<>(newRequestList,
            multipleRequests.getTotalRecords()));
        }
      ));
  }

  private Function<Request, Request> getRequestMapper(MultipleRecords<Instance> multipleInstances) {
    return request -> multipleInstances.getRecords().stream()
      .filter(matchedInstanceId(request))
      .findFirst()
      .map(request::withInstance)
      .orElseGet(() -> {
        log.error("No instance found for request {} (instanceId {})", request.getId(), request.getInstanceId());
        return request;
      });
  }

  private Predicate<Instance> matchedInstanceId(Request request) {
    if (request.getInstanceId() == null) {
      log.error("InstanceId is NULL for request {}", request.getId());
      return instance -> false;
    }
    return instance -> request.getInstanceId().equals(instance.getId());
  }

}
