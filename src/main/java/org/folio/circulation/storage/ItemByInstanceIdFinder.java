package org.folio.circulation.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.JsonKeys.byId;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;

import io.vertx.core.json.JsonObject;

public class ItemByInstanceIdFinder {

  private final GetManyRecordsClient holdingsStorageClient;
  private final ItemRepository itemRepository;

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
