package org.folio.circulation.storage;

import static org.folio.circulation.support.results.Result.of;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;

public class ItemByIdInStorageFinder {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ItemRepository itemRepository;
  private final Supplier<HttpFailure> itemNotFoundFailureSupplier;

  public ItemByIdInStorageFinder(
    ItemRepository itemRepository,
    Supplier<HttpFailure> itemNotFoundFailureSupplier) {

    this.itemRepository = itemRepository;
    this.itemNotFoundFailureSupplier = itemNotFoundFailureSupplier;
  }

  public CompletableFuture<Result<Item>> findItemById(String itemId) {
    log.debug("findItemById:: parameters itemId: {}", itemId);
    return itemRepository.fetchById(itemId)
      .thenApply(itemResult -> failWhenNoItemFoundForId(itemResult,
        itemNotFoundFailureSupplier));
  }

  private static Result<Item> failWhenNoItemFoundForId(
    Result<Item> itemResult,
    Supplier<HttpFailure> itemNotFoundFailureSupplier) {

    return itemResult.failWhen(item -> of(item::isNotFound),
      item -> itemNotFoundFailureSupplier.get());
  }
}
