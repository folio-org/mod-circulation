package org.folio.circulation.support.results;

import java.util.concurrent.CompletionStage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AsynchronousResult<T> {
  private final CompletionStage<Result<T>> completionStage;

  public static <T> AsynchronousResult<T> fromFutureResult(CompletionStage<Result<T>> futureResult) {
    return new AsynchronousResult<>(futureResult);
  }

  public CompletionStage<Result<T>> toCompletionStage() {
    return completionStage.toCompletableFuture().copy();
  }
}
