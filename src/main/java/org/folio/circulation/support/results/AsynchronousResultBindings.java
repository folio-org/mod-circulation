package org.folio.circulation.support.results;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class AsynchronousResultBindings {
  private AsynchronousResultBindings() { }

  public static <T> CompletableFuture<Result<T>> safelyInitialise(
    Supplier<CompletableFuture<Result<T>>> supplier) {

    if (supplier == null) {
      return completedFuture(failedDueToServerError(new NullPointerException(
        "The asynchronous result supplier should not be null")));
    }

    try {
      return supplier.get();
    }
    catch(Exception e) {
      return completedFuture(failedDueToServerError(e));
    }
  }

  public static <T, R, S> Function<Result<T>, CompletableFuture<Result<S>>> combineAfter(
    Function<T, CompletableFuture<Result<R>>> action,
    BiFunction<T, R, S> combiner) {

    return r -> r.combineAfter(action, combiner);
  }
}
