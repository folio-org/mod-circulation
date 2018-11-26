package org.folio.circulation.storage;

import static org.folio.circulation.support.HttpResult.of;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.folio.circulation.domain.Item;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.ValidationErrorFailure;

public class ItemByBarcodeInStorageFinder {
  public CompletableFuture<HttpResult<Item>> findItemByBarcode(
    String itemBarcode,
    Supplier<ValidationErrorFailure> itemNotFoundFailureSupplier,
    ItemRepository itemRepository) {

    return itemRepository.fetchByBarcode(itemBarcode)
      .thenApply(itemResult -> failWhenNoItemFoundForBarcode(itemResult,
        itemNotFoundFailureSupplier));
  }

  private static HttpResult<Item> failWhenNoItemFoundForBarcode(
    HttpResult<Item> itemResult,
    Supplier<ValidationErrorFailure> itemNotFoundFailureSupplier) {

    return itemResult.failWhen(item -> of(item::isNotFound),
      item -> itemNotFoundFailureSupplier.get());
  }
}
