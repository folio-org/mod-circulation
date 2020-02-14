package org.folio.circulation.storage;

import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForBarcodeFailure;
import static org.folio.circulation.support.Result.of;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

public class ItemByBarcodeInStorageFinder {
  private final ItemRepository itemRepository;

  public ItemByBarcodeInStorageFinder(ItemRepository itemRepository) {
    this.itemRepository = itemRepository;
  }

  public CompletableFuture<Result<Item>> findItemByBarcode(String itemBarcode) {
    return itemRepository.fetchByBarcode(itemBarcode)
      .thenApply(itemResult -> failWhenNoItemFoundForBarcode(itemResult, itemBarcode));
  }

  private static Result<Item> failWhenNoItemFoundForBarcode(
    Result<Item> itemResult, String itemBarcode) {

    return itemResult.failWhen(item -> of(item::isNotFound),
      item -> noItemFoundForBarcodeFailure(itemBarcode).get());
  }
}
