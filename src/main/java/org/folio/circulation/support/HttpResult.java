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
      return failed(firstResult.cause());
    }
    else if(secondResult.failed()) {
      return failed(secondResult.cause());
    }
    else {
      return succeeded(combiner.apply(firstResult.value(),
        secondResult.value()));
    }
  }

  /**
   * Combines a result together with the result of an action, if both succeed.
   * If the first result is a failure then it is returned, and the action is not invoked
   * otherwise if the result of the action is a failure it is returned
   *
   * @param nextAction the action to invoke if the current result succeeded
   * @param combiner function to combine the values together
   * @return either failure from the first result, failure from the action
   * or successful result with the values combined
   */
  default <U, V> CompletableFuture<HttpResult<V>> combineAfter(
    Function<T, CompletableFuture<HttpResult<U>>> nextAction, BiFunction<
    T, U, V> combiner) {

    return this.after(nextAction)
      .thenApply(actionResult -> actionResult.map(r ->
        combiner.apply(value(), r)));
  }
  
  boolean failed();

  T value();
  HttpFailure cause();

  default boolean succeeded() {
    return !failed();
  }

  static <T> HttpResult<T> succeeded(T value) {
    return new SuccessfulHttpResult<>(value);
  }

  static <T> WritableHttpResult<T> failed(HttpFailure cause) {
    return new FailedHttpResult<>(cause);
  }

  default <R> CompletableFuture<HttpResult<R>> after(
    Function<T, CompletableFuture<HttpResult<R>>> action) {

    if(failed()) {
      return CompletableFuture.completedFuture(failed(cause()));
    }

    return action.apply(value());
  }

  default <R> HttpResult<R> next(Function<T, HttpResult<R>> action) {
    if(failed()) {
      return failed(cause());
    }

    return action.apply(value());
  }

  default <U> HttpResult<U> map(Function<T, U> map) {
    if(failed()) {
      return failed(cause());
    }
    else {
      return HttpResult.succeeded(map.apply(value()));
    }
  }

  default T orElse(T other) {
    return succeeded()
      ? value()
      : other;
  }
}
