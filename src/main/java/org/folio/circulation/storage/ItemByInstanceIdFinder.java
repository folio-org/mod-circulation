package org.folio.circulation.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.json.JsonKeys.byId;
import static org.folio.circulation.support.results.Result.*;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class ItemByInstanceIdFinder {

  private final GetManyRecordsClient holdingsStorageClient;
  private final ItemRepository itemRepository;
  private static final String HOLDINGS_RECORD_ID = "holdingsRecordId";

  public ItemByInstanceIdFinder(GetManyRecordsClient holdingsStorageClient,
                                ItemRepository itemRepository) {

    this.holdingsStorageClient = holdingsStorageClient;
    this.itemRepository = itemRepository;
  }

  public CompletableFuture<Result<Collection<Item>>> getItemsByInstanceId(UUID instanceId) {

    final FindWithCqlQuery<JsonObject> fetcher = findWithCqlQuery(
      holdingsStorageClient, "holdingsRecords", identity());

    return fetcher.findByQuery(CqlQuery.exactMatch("instanceId", instanceId.toString()))
      .thenCompose(this::getItems);
  }

  public CompletableFuture<Result<Item>> getFirstAvailableItemByInstanceId(
    String instanceId) {

    final FindWithCqlQuery<JsonObject> fetcher = findWithCqlQuery(
      holdingsStorageClient, "holdingsRecords", identity());

    return fetcher.findByQuery(CqlQuery.exactMatch("instanceId", instanceId))
      .thenCompose(this::getAvailableItem);
  }

  private CompletableFuture<Result<Item>> getAvailableItem(
    Result<MultipleRecords<JsonObject>> holdingsRecordsResult
  ) {
    return holdingsRecordsResult.after(holdingsRecords -> {
      if (holdingsRecords == null || holdingsRecords.isEmpty()) {
        return completedFuture(succeeded(Item.from(null)));
      }

      Set<String> holdingsIds = holdingsRecords.toKeys(byId());

      return itemRepository.findByIndexNameAndQuery(holdingsIds, HOLDINGS_RECORD_ID,
        CqlQuery.exactMatch("status.name", ItemStatus.AVAILABLE.getValue()))
        .thenApply(r -> r.map(items -> items.stream().findFirst().orElse(null)));
    });
  }

  private CompletableFuture<Result<Collection<Item>>> getItems(
    Result<MultipleRecords<JsonObject>> holdingsRecordsResult) {

    return holdingsRecordsResult.after(holdingsRecords -> {
      if (holdingsRecords == null || holdingsRecords.isEmpty()) {
        return completedFuture(failedValidation(
          "There are no holdings for this instance", "holdingsRecords", "null"));
      }

      Set<String> holdingsIds = holdingsRecords.toKeys(byId());

      return itemRepository.findByQuery(exactMatchAny("holdingsRecordId", holdingsIds));
    });
  }
}
