package org.folio.circulation.storage;

import static org.folio.circulation.support.Result.of;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.folio.circulation.domain.Item;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

public class ItemByIdInStorageFinder {
  private final ItemRepository itemRepository;
  private final Supplier<HttpFailure> itemNotFoundFailureSupplier;

  public ItemByIdInStorageFinder(
    ItemRepository itemRepository,
    Supplier<HttpFailure> itemNotFoundFailureSupplier) {

    this.itemRepository = itemRepository;
    this.itemNotFoundFailureSupplier = itemNotFoundFailureSupplier;
  }

  public CompletableFuture<Result<Item>> findItemById(String itemId) {
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
