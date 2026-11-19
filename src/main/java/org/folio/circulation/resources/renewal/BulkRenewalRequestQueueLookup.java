package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.RequestStatus.openStates;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.support.results.Result;

public class BulkRenewalRequestQueueLookup {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final int REQUEST_ID_CHUNK_SIZE = BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE;

  private final Function<Collection<String>, CompletableFuture<Result<Collection<Request>>>>
    itemRequestFetcher;
  private final Function<Collection<String>, CompletableFuture<Result<Collection<Request>>>>
    instanceRequestFetcher;

  public BulkRenewalRequestQueueLookup(
    Function<Collection<String>, CompletableFuture<Result<Collection<Request>>>> itemRequestFetcher,
    Function<Collection<String>, CompletableFuture<Result<Collection<Request>>>> instanceRequestFetcher) {

    this.itemRequestFetcher = itemRequestFetcher;
    this.instanceRequestFetcher = instanceRequestFetcher;
  }

  public CompletableFuture<Result<Map<String, RequestQueue>>> lookupByLoanId(Collection<Loan> loans,
    TlrSettingsConfiguration tlrSettings) {

    if (CollectionUtils.isEmpty(loans)) {
      log.info("lookupByLoanId:: No Loans are provided, skipping Request Queue Lookup");
      return ofAsync(Map.of());
    }

    return tlrSettings != null && tlrSettings.isTitleLevelRequestsFeatureEnabled()
      ? lookupByInstanceId(loans)
      : lookupByItemId(loans);
  }

  private CompletableFuture<Result<Map<String, RequestQueue>>> lookupByItemId(Collection<Loan> loans) {
    log.info("lookupByItemId:: Looking up Request Queues by Item IDs");
    var itemIds = loans.stream()
      .map(Loan::getItemId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    if (itemIds.isEmpty()) {
      log.info("lookupByItemId:: No Item IDs for {} loans, returning empty Request Queues for the Loans", loans.size());
      return ofAsync(emptyQueuesByLoanId(loans));
    }

    return fetchRequestsInChunks(itemIds, itemRequestFetcher)
      .thenApply(result -> result.map(requests -> buildItemLevelQueues(loans, requests)));
  }

  private CompletableFuture<Result<Map<String, RequestQueue>>> lookupByInstanceId(Collection<Loan> loans) {
    log.info("lookupByInstanceId:: Looking up Request Queues by Instance IDs");
    var instanceIds = loans.stream()
      .map(Loan::getItem)
      .filter(Objects::nonNull)
      .map(Item::getInstanceId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    if (instanceIds.isEmpty()) {
      log.info("lookupByInstanceId:: No Instance IDs for {} loans, returning empty Request Queues for the Loans", loans.size());
      return ofAsync(emptyQueuesByLoanId(loans));
    }

    return fetchRequestsInChunks(instanceIds, instanceRequestFetcher)
      .thenApply(result -> result.map(requests -> buildInstanceLevelQueues(loans, requests)));
  }

  private CompletableFuture<Result<Collection<Request>>> fetchRequestsInChunks(
    Collection<String> ids,
    Function<Collection<String>, CompletableFuture<Result<Collection<Request>>>> fetcher) {

    List<List<String>> partitions = BulkRenewalChunkedFetchSupport.partition(ids,
      REQUEST_ID_CHUNK_SIZE);

    if (partitions.isEmpty()) {
      return ofAsync(List.of());
    }

    CompletableFuture<Result<MultipleRecords<Request>>> combined = completedFuture(
      succeeded(MultipleRecords.<Request>empty()));

    for (List<String> partition : partitions) {
      combined = combined.thenCompose(existing -> existing.combineAfter(
        ignored -> fetcher.apply(partition).thenApply(result -> result.map(requests ->
          new MultipleRecords<>(List.copyOf(requests), requests.size()))),
        MultipleRecords::combine));
    }

    return combined.thenApply(result -> result.map(MultipleRecords::getRecords));
  }

  private Map<String, RequestQueue> buildItemLevelQueues(Collection<Loan> loans,
    Collection<Request> requests) {

    Map<String, List<Request>> requestsByItemId = requests.stream()
      .filter(Objects::nonNull)
      .filter(this::isOpenRequest)
      .filter(Request::isItemLevel)
      .filter(request -> request.getItemId() != null)
      .collect(Collectors.groupingBy(Request::getItemId));

    return loans.stream()
      .collect(Collectors.toMap(Loan::getId,
        loan -> new RequestQueue(requestsByItemId.getOrDefault(loan.getItemId(), List.of()))));
  }

  private Map<String, RequestQueue> buildInstanceLevelQueues(Collection<Loan> loans,
    Collection<Request> requests) {

    Map<String, List<Request>> requestsByInstanceId = requests.stream()
      .filter(Objects::nonNull)
      .filter(this::isOpenRequest)
      .filter(request -> request.isItemLevel() || request.isTitleLevel())
      .filter(request -> request.getInstanceId() != null)
      .collect(Collectors.groupingBy(Request::getInstanceId));

    return loans.stream()
      .collect(Collectors.toMap(Loan::getId,
        loan -> new RequestQueue(requestsByInstanceId.getOrDefault(instanceIdOf(loan), List.of()))));
  }

  private Map<String, RequestQueue> emptyQueuesByLoanId(Collection<Loan> loans) {
    return loans.stream()
      .collect(Collectors.toMap(Loan::getId, ignored -> new RequestQueue(List.of())));
  }

  private String instanceIdOf(Loan loan) {
    return loan.getItem() != null ? loan.getItem().getInstanceId() : null;
  }

  private boolean isOpenRequest(Request request) {
    RequestStatus status = request.getStatus();
    return status != null && openStates().contains(status.getValue());
  }
}
