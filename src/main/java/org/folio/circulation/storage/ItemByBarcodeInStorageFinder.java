package org.folio.circulation.storage;

import static org.folio.circulation.support.Result.of;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.folio.circulation.domain.Item;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ItemRepository;

public class ItemByBarcodeInStorageFinder {
  private final ItemRepository itemRepository;
  private final Supplier<HttpFailure> itemNotFoundFailureSupplier;

  public ItemByBarcodeInStorageFinder(
    ItemRepository itemRepository,
    Supplier<HttpFailure> itemNotFoundFailureSupplier) {

    this.itemRepository = itemRepository;
    this.itemNotFoundFailureSupplier = itemNotFoundFailureSupplier;
  }

  public CompletableFuture<Result<Item>> findItemByBarcode(String itemBarcode) {
    return itemRepository.fetchByBarcode(itemBarcode)
      .thenApply(itemResult -> failWhenNoItemFoundForBarcode(itemResult,
        itemNotFoundFailureSupplier));
  }

  private static Result<Item> failWhenNoItemFoundForBarcode(
    Result<Item> itemResult,
    Supplier<HttpFailure> itemNotFoundFailureSupplier) {

    return itemResult.failWhen(item -> of(item::isNotFound),
      item -> itemNotFoundFailureSupplier.get());
  }
}
