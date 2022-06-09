package org.folio.circulation.support.results;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import org.folio.circulation.support.HttpFailure;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AsynchronousResult<T> {
  private final CompletionStage<Result<T>> completionStage;

  /**
   * Initialises a {@link org.folio.circulation.support.results.AsynchronousResult} from a result that could be completed in the future
   *
   * Mostly for interoperability with the previous result handling
   *
   * @param futureResult the future result to use for initialisation
   * @param <T> type of the value of a successful result
   * @return a new {@link org.folio.circulation.support.results.AsynchronousResult}
   */
  public static <T> AsynchronousResult<T> fromFutureResult(CompletionStage<Result<T>> futureResult) {
    return new AsynchronousResult<>(futureResult);
  }

  /**
   * Initialises a successful {@link org.folio.circulation.support.results.AsynchronousResult}
   *
   * @param value value of the successful result
   * @param <T> type of the value of a successful result
   * @return a new {@link org.folio.circulation.support.results.AsynchronousResult}
   */
  public static <T> AsynchronousResult<T> successful(T value) {
    return new AsynchronousResult<>(completedFuture(Result.of(() -> value)));
  }

  /**
   * Initialises a failed {@link org.folio.circulation.support.results.AsynchronousResult}
   *
   * @param cause cause of the failure
   * @param <T> type of the value of a successful result
   * @return a new {@link org.folio.circulation.support.results.AsynchronousResult}
   */
  public static <T> AsynchronousResult<T> failure(HttpFailure cause) {
    return new AsynchronousResult<>(completedFuture(Result.failed(cause)));
  }

  /**
   * Converts an {@link org.folio.circulation.support.results.AsynchronousResult} to a completion stage of a result
   *
   * Mostly for interoperability with the previous result handling
   *
   * @return a new {@link java.util.concurrent.CompletableFuture} of a result
   */
  public CompletionStage<Result<T>> toCompletionStage() {
    return completionStage.toCompletableFuture().copy();
  }

  /**
   * Maps a successful result to a new {@link org.folio.circulation.support.results.AsynchronousResult} with the inner value of the applied map function
   *
   * Preserves the pre-existing failure if the current result is a failure
   *
   * Not strictly a flat map, mostly for interoperability with the previous result handling
   *
   * @param map function to map this to a new {@link org.folio.circulation.support.results.AsynchronousResult}
   * @param <R> Type of the successful result following the mapping
   * @return a successful {@link org.folio.circulation.support.results.AsynchronousResult}
   * when the current result is successful and the mapping succeeds, otherwise a failure
   */
  public <R> AsynchronousResult<R> flatMapFuture(Function<T, CompletableFuture<Result<R>>> map) {
    return fromFutureResult(completionStage.thenComposeAsync(r -> r.after(map)));
  }

  public <R> AsynchronousResult<R> map(Function<T, R> mapper) {
    return fromFutureResult(completionStage.thenApply(r -> r.map(mapper)));
  }

  public AsynchronousResult<T> onSuccess(Consumer<T> consumer) {
    return run(r -> {
      if (r.succeeded()) {
        consumer.accept(r.value());
      }
    });
  }

  public AsynchronousResult<T> onFailure(Consumer<HttpFailure> consumer) {
    return run(r -> {
      if (r.failed()) {
        consumer.accept(r.cause());
      }
    });
  }

  public AsynchronousResult<T> onComplete(Consumer<T> onSuccess, Consumer<HttpFailure> onFailure) {
    return onSuccess(onSuccess).onFailure(onFailure);
  }

  private AsynchronousResult<T> run(Consumer<Result<T>> consumer) {
    return fromFutureResult(completionStage.thenCompose(r -> {
      consumer.accept(r);

      return completionStage;
    }));
  }

  public CompletableFuture<Result<T>> toCompletableFuture() {
    return completionStage.toCompletableFuture();
  }
}
