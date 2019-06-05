package org.folio.circulation.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.JsonKeys.byId;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class ItemByInstanceIdFinder {

  private CollectionResourceClient holdingsStorageClient;
  private CollectionResourceClient itemsStorageClient;

  public ItemByInstanceIdFinder(CollectionResourceClient holdingsStorageClient,
                                CollectionResourceClient itemsStorageClient) {
    this.holdingsStorageClient = holdingsStorageClient;
    this.itemsStorageClient = itemsStorageClient;
  }

  public CompletableFuture<Result<Collection<Item>>> getItemsByInstanceId(String instanceId) {

    final MultipleRecordFetcher<JsonObject> fetcher
      = new MultipleRecordFetcher<>(holdingsStorageClient, "holdingsRecords", identity());

    return fetcher.findByQuery(CqlQuery.exactMatch("instanceId", instanceId))
      .thenCompose(this::getItems);
  }

  private CompletableFuture<Result<Collection<Item>>> getItems(
    Result<MultipleRecords<JsonObject>> holdingsRecordsResult) {

    return holdingsRecordsResult.after(holdingsRecords -> {
      if (holdingsRecords == null || holdingsRecords.isEmpty()) {
        return completedFuture(failedValidation(
          "holdingsRecords is null or empty", "holdingsRecords", "null"));
      }

      List<String> holdingsIds = holdingsRecords.toKeys(byId());

      final MultipleRecordFetcher<Item> fetcher
        = new MultipleRecordFetcher<>(itemsStorageClient, "items", Item::from);

      return fetcher.findByIndexName(holdingsIds, "holdingsRecordId")
        .thenApply(r -> r.map(MultipleRecords::getRecords));
    });
  }
}
