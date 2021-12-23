package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
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

    final FindWithMultipleCqlIndexValues<Instance> fetcher = createInstancesFetcher();

    return fetcher.findByIds(instanceIdsToFetch)
      .thenApply(multipleInstancesResult -> multipleInstancesResult.next(
        multipleInstances -> {
          List<Request> newRequestList = new ArrayList<>();
          Collection<Instance> instanceCollection = multipleInstances.getRecords();
          for (Request request : requests) {
            Request newRequest = null;
            boolean foundInstance = false;
            for (Instance instance : instanceCollection) {
              if (request.getInstanceId() != null && request.getInstanceId().equals(instance.getId())) {
                newRequest = request.withInstance(instance);
                foundInstance = true;
                break;
              }
            }
            if (!foundInstance) {
              log.info("No instance (out of {}) found for request {} (instanceId {})",
                instanceCollection.size(), request.getId(), request.getInstanceId());
              newRequest = request;
            }
            newRequestList.add(newRequest);
          }
          return succeeded(new MultipleRecords<>(newRequestList,
            multipleRequests.getTotalRecords()));
        }
      ));
  }

  private FindWithMultipleCqlIndexValues<Instance> createInstancesFetcher() {
    return findWithMultipleCqlIndexValues(instancesClient, "instances", Instance::from);
  }
}
