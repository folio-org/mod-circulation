package org.folio.circulation.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.json.JsonKeys.byId;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class ItemByInstanceIdFinder {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final GetManyRecordsClient holdingsStorageClient;
  private final ItemRepository itemRepository;

  public ItemByInstanceIdFinder(GetManyRecordsClient holdingsStorageClient,
                                ItemRepository itemRepository) {

    this.holdingsStorageClient = holdingsStorageClient;
    this.itemRepository = itemRepository;
  }

  public CompletableFuture<Result<Collection<Item>>> getItemsByInstanceId(UUID instanceId,
    boolean failWhenNoHoldingsRecordsFound) {

    final FindWithCqlQuery<JsonObject> fetcher = findWithCqlQuery(
      holdingsStorageClient, "holdingsRecords", identity());

    return fetcher.findByQuery(CqlQuery.exactMatch("instanceId", instanceId.toString()),
        PageLimit.oneThousand())
      .thenCompose(r -> getItems(r, failWhenNoHoldingsRecordsFound));
  }

  private CompletableFuture<Result<Collection<Item>>> getItems(
    Result<MultipleRecords<JsonObject>> holdingsRecordsResult,
    boolean failWhenNoHoldingsRecordsFound) {

    return holdingsRecordsResult.after(holdingsRecords -> {
      log.info("getItems:: holdings records: {}", holdingsRecords.getRecords().stream()
        .map(h -> h.getString("id"))
        .collect(Collectors.joining(", ")));

      if (holdingsRecords == null || holdingsRecords.isEmpty()) {
        if (failWhenNoHoldingsRecordsFound) {
          return completedFuture(failedValidation(
            "There are no holdings for this instance", "holdingsRecords", "null"));
        }
        return completedFuture(succeeded(List.of()));
      }

      Set<String> holdingsIds = holdingsRecords.toKeys(byId());

      return itemRepository.findBy("holdingsRecordId", holdingsIds);
    });
  }
}
