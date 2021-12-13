package org.folio.circulation.infrastructure.storage.inventory;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CirculationItemRepository {

  private final CollectionResourceClient circulationItemsClient;

  public CirculationItemRepository(Clients clients) {
    this(clients.circulationItemsStorage());
  }

  public CompletableFuture<Result<Item>> fetchByBarcode(String barcode) {
    return fetchAsJson(barcode)
      .thenApply(r -> r.map(CirculationItemMapper::toItem));
  }

  private CompletableFuture<Result<JsonObject>> fetchAsJson(String barcode) {
    return SingleRecordFetcher.jsonOrNull(circulationItemsClient, "circulation item")
      .fetch(barcode);
  }

}
