package org.folio.circulation.support.items;

import org.folio.circulation.support.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ItemBatchUtils {

  /**
   * The optimal number of identifiers that will not exceed the permissible length
   * of the URI in according to the RFC 2616
   */
  private static final int BATCH_SIZE = 40;

  private ItemBatchUtils(){

  }

  public static CompletableFuture<Result<List<List<String>>>> mapItemIdsInBatchItemIds(List<String> itemIds) {
    return CompletableFuture.completedFuture(Result.succeeded(splitIds(itemIds)));
  }

  private static List<List<String>> splitIds(List<String> itemsIds) {
    int size = itemsIds.size();
    if (size <= 0) {
      return new ArrayList<>();
    }

    int fullChunks = (size - 1) / BATCH_SIZE;
    return IntStream.range(0, fullChunks + 1)
      .mapToObj(n ->
        itemsIds.subList(n * BATCH_SIZE, n == fullChunks
          ? size
          : (n + 1) * BATCH_SIZE))
      .collect(Collectors.toList());
  }
}
