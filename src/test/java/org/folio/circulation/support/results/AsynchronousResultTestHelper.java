package org.folio.circulation.support.results;

import java.util.concurrent.TimeUnit;

import org.folio.circulation.support.HttpFailure;

import lombok.SneakyThrows;

class AsynchronousResultTestHelper {
  @SneakyThrows
  public static <T> T getValue(AsynchronousResult<T> asynchronousResult, int timeout, TimeUnit unit) {
    return get(asynchronousResult, timeout, unit).value();
  }

  @SneakyThrows
  public static <T> HttpFailure getCause(AsynchronousResult<T> asynchronousResult, int timeout, TimeUnit unit) {
    return get(asynchronousResult, timeout, unit).cause();
  }

  @SneakyThrows
  private static <T> Result<T> get(AsynchronousResult<T> asynchronousResult, int timeout, TimeUnit unit) {
    return asynchronousResult.toCompletionStage().toCompletableFuture().get(timeout, unit);
  }
}
