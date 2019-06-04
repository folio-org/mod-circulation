package org.folio.circulation.storage;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;


import org.apache.commons.httpclient.HttpException;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonArray;
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

     return holdingsStorageClient.getManyWithRawQueryStringParameters("query=instanceId=" + instanceId)
           .thenApply(this::extractHoldings)
           .thenCompose(this::getItems);
  }

  public JsonArray extractHoldings(Response response) {

    if (response.getStatusCode() != 200) {
      throw new CompletionException(new HttpException(response.getBody()));
    }
    JsonObject responseJson = response.getJson();

    return responseJson.getJsonArray("holdingsRecords");
  }


  private CompletableFuture<Result<Collection<Item>>> getItems(JsonArray holdingsRecords) {

    if (holdingsRecords == null || holdingsRecords.isEmpty()) {
      CompletableFuture completableFuture = new CompletableFuture();
      return completableFuture.thenCompose( x -> failedValidation("holdingsRecords is null or empty", "holdingsRecords", "null"));
    }

    List<String> holdingsRecIds = new ArrayList<>();

    for (Object o : holdingsRecords) {
      if (o instanceof JsonObject) {
        JsonObject jo = (JsonObject) o;
        try {
          holdingsRecIds.add((jo).getString("id"));
        } catch (Exception e) {
          throw new CompletionException(e);
        }
      }
    }

    final MultipleRecordFetcher<Item> fetcher
      = new MultipleRecordFetcher<>(itemsStorageClient, "items", Item::from);

    return fetcher.findByIndexName(holdingsRecIds, "holdingsRecordId")
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }
}
