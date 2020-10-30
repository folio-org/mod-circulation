package org.folio.circulation.support.fetching;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.http.client.Offset.zeroOffset;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.Result.failed;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class PageableFetcher<T> {
  private static final Logger log = getLogger(PageableFetcher.class);

  // This limit needed to prevent stack overflow for recursive fetch
  private static final int DEFAULT_MAX_ALLOWED_RECORDS_LIMIT = 1_000_000;
  private static final PageLimit DEFAULT_PAGE_SIZE_LIMIT = limit(500);

  private final GetManyRecordsRepository<T> repository;
  private final PageLimit pageSize;
  private final int maxAllowedRecordsToFetchLimit;

  public PageableFetcher(GetManyRecordsRepository<T> repository) {
    this(repository, DEFAULT_PAGE_SIZE_LIMIT, DEFAULT_MAX_ALLOWED_RECORDS_LIMIT);
  }

  public CompletableFuture<Result<Void>> processPages(CqlQuery query, PageProcessor<T> pageProcessor) {
    return processPagesRecursively(query, pageProcessor, zeroOffset(), 0);
  }

  private CompletableFuture<Result<Void>> processPagesRecursively(CqlQuery query,
    PageProcessor<T> pageProcessor, Offset currentOffset, int recordsFetchedOnPreviousIteration) {

    return repository.getMany(query, pageSize, currentOffset)
      .thenCompose(r -> r.after(records -> pageProcessor.processPage(records)
          .thenCompose(processResult -> processResult.after(unused -> {
            final int recordsFetchedSoFar = recordsFetchedOnPreviousIteration + records.size();

            if (hasFetchedAllPages(records)) {
              log.info("All pages have been fetched, total records fetched {}", recordsFetchedSoFar);

              return completedFuture(processResult);
            } else if (hasReachedRecordsLimit(recordsFetchedSoFar)) {
              log.warn("Terminating fetching because records limit in {} has been reached",
                maxAllowedRecordsToFetchLimit);

              return itemCountLimitHasBeenReached();
            } else {
              final var nextOffset = currentOffset.nextPage(pageSize);
              return processPagesRecursively(query, pageProcessor, nextOffset, recordsFetchedSoFar);
            }
          }))
      ));
  }

  private CompletableFuture<Result<Void>> itemCountLimitHasBeenReached() {
    return completedFuture(failed(new ServerErrorFailure(
      "Maximum allowed item count is set to " + maxAllowedRecordsToFetchLimit
        + " and it has been reached")));
  }

  private boolean hasReachedRecordsLimit(int recordFetchedSoFar) {
    return recordFetchedSoFar >= maxAllowedRecordsToFetchLimit;
  }

  private boolean hasFetchedAllPages(MultipleRecords<T> latestPage) {
    if (latestPage.isEmpty()) {
      return true;
    }

    return latestPage.size() < pageSize.getLimit();
  }
}
