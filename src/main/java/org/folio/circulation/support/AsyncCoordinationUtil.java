package org.folio.circulation.support;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AsyncCoordinationUtil {

  private AsyncCoordinationUtil() {
    throw new UnsupportedOperationException();
  }

  /**
   * Applies {@code asyncAction} to all the elements in {@code collection}
   * and combines results to list
   */
  public static <T, R> CompletableFuture<Result<List<R>>> allOf(
    Collection<T> collection, Function<T, CompletableFuture<Result<R>>> asyncAction) {

    return allResultsOf(collection, asyncAction)
      .thenApply(Result::combineAll);
  }

  /**
   * Applies {@code asyncAction} to all the elements in {@code collection}
   * and returns a CompletableFuture with a list of all results
   */
  public static <T, R> CompletableFuture<List<Result<R>>> allResultsOf(
    Collection<T> collection,
    Function<T, CompletableFuture<Result<R>>> asyncAction) {

    List<CompletableFuture<Result<R>>> futures =
      collection.stream().map(asyncAction).collect(Collectors.toList());

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
  }

}
