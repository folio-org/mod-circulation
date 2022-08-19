package org.folio.circulation.support;

import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.support.results.Result;

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

  public static <T, K, V> CompletableFuture<Result<Map<K, V>>> allOf(
    Collection<T> collection, Function<T, K> keyMapper,
    Function<T, CompletableFuture<Result<V>>> asyncAction) {

    List<CompletableFuture<Result<Pair<K, V>>>> futures = collection.stream()
      .map(t -> keyValueFuture(t, keyMapper, asyncAction))
      .collect(Collectors.toList());

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> futures.stream()
        .map(CompletableFuture::join)
        .reduce(succeeded(new HashMap<>()), AsyncCoordinationUtil::pairAccumulator, (a, b) -> a));
  }

  private static <T, K, V> CompletableFuture<Result<Pair<K, V>>> keyValueFuture(T value,
    Function<T, K> keyMapper, Function<T, CompletableFuture<Result<V>>> asyncAction) {

    return asyncAction.apply(value)
      .thenApply(r -> r.map(v -> Pair.of(keyMapper.apply(value), v)));
  }

  private static <K, V> Result<Map<K, V>> pairAccumulator(Result<Map<K, V>> map,
    Result<Pair<K, V>> pair) {

    return map.combine(pair, (m, p) -> {
      m.put(p.getKey(), p.getValue());
      return m;
    });
  }
}
