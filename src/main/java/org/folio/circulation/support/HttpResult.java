package org.folio.circulation.support;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface HttpResult<T> {
  /**
   * Combines two results together, if both succeed.
   * Otherwise, returns either failure, first result takes precedence
   *
   * @param firstResult the  first result
   * @param secondResult the  second result
   * @param combiner function to combine the values together
   * @return either failure from the first result, failure from the second
   * or successful result with the values combined
   */
  static <T, U, V> HttpResult<V> combine(
    HttpResult<T> firstResult,
    HttpResult<U> secondResult,
    BiFunction<T, U, V> combiner) {

    if(firstResult.failed()) {
      return failure(firstResult.cause());
    }
    else if(secondResult.failed()) {
      return failure(secondResult.cause());
    }
    else {
      return success(combiner.apply(firstResult.value(),
        secondResult.value()));
    }
  }

  boolean failed();

  T value();
  HttpFailure cause();

  default boolean succeeded() {
    return !failed();
  }

  static <T> HttpResult<T> success(T value) {
    return new SuccessfulHttpResult<>(value);
  }

  static <T> WritableHttpResult<T> failure(HttpFailure cause) {
    return new FailedHttpResult<>(cause);
  }

  default <R> CompletableFuture<HttpResult<R>> after(
    Function<T, CompletableFuture<HttpResult<R>>> action) {

    if(failed()) {
      return CompletableFuture.completedFuture(failure(cause()));
    }

    return action.apply(value());
  }

  default <R> HttpResult<R> next(Function<T, HttpResult<R>> action) {
    if(failed()) {
      return failure(cause());
    }

    return action.apply(value());
  }

  default <U> HttpResult<U> map(Function<T, U> map) {
    if(failed()) {
      return failure(cause());
    }
    else {
      return HttpResult.success(map.apply(value()));
    }
  }

  default T orElse(T other) {
    return succeeded()
      ? value()
      : other;
  }
}
