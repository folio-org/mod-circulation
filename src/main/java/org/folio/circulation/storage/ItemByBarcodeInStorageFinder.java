package org.folio.circulation.storage;

import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForBarcodeFailure;
import static org.folio.circulation.support.results.Result.of;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.results.Result;

public class ItemByBarcodeInStorageFinder {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ItemRepository itemRepository;

  public ItemByBarcodeInStorageFinder(ItemRepository itemRepository) {
    this.itemRepository = itemRepository;
  }

  public CompletableFuture<Result<Item>> findItemByBarcode(String itemBarcode) {
    log.debug("findItemByBarcode:: parameters itemBarcode: {}", itemBarcode);
    return itemRepository.fetchByBarcode(itemBarcode)
      .thenApply(itemResult -> failWhenNoItemFoundForBarcode(itemResult, itemBarcode));
  }

  private static Result<Item> failWhenNoItemFoundForBarcode(
    Result<Item> itemResult, String itemBarcode) {

    return itemResult.failWhen(item -> of(item::isNotFound),
      item -> noItemFoundForBarcodeFailure(itemBarcode).get());
  }
}
