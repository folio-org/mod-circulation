package org.folio.circulation.support;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface HttpResult<T> {
  boolean failed();
  boolean succeeded();

  T value();
  HttpFailure cause();

  static <T> HttpResult<T> success(T value) {
    return new SuccessfulHttpResult<>(value);
  }

  static <T> WritableHttpResult<T> failure(HttpFailure cause) {
    return new FailedHttpResult<>(cause);
  }

  default <R> CompletableFuture<HttpResult<R>> next(
    Function<T, CompletableFuture<HttpResult<R>>> action) {

    if(failed()) {
      return CompletableFuture.completedFuture(failure(cause()));
    }

    return action.apply(value());
  }
}
