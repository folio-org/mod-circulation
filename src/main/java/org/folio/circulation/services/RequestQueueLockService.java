package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.RequestQueueKey;
import org.folio.circulation.domain.RequestQueueLock;
import org.folio.circulation.domain.configuration.RequestQueueLockConfiguration;
import org.folio.circulation.infrastructure.storage.RequestQueueLockRepository;
import org.folio.circulation.support.ServiceUnavailableFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.Vertx;

public class RequestQueueLockService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String LOCK_TIMEOUT = "REQUEST_QUEUE_LOCK_TIMEOUT";

  private final RequestQueueLockRepository repository;
  private final Vertx vertx;

  public RequestQueueLockService(RequestQueueLockRepository repository, Vertx vertx) {
    this.repository = repository;
    this.vertx = vertx;
  }

  public <T> CompletableFuture<Result<T>> execute(RequestQueueKey key,
    RequestQueueLockConfiguration configuration,
    Supplier<CompletableFuture<Result<T>>> criticalSection) {

    if (!configuration.isRequestQueueLockFeatureEnabled()) {
      log.warn("Request queue locking is disabled for {}:{}", key.queueType(), key.queueId());
      return invoke(criticalSection);
    }

    long startedAt = System.nanoTime();
    return acquire(key, configuration, startedAt, 1)
      .thenCompose(acquired -> acquired.succeeded()
        ? executeAndRelease(key, acquired.value(), criticalSection, elapsedMs(startedAt))
        : completedFuture(failed(acquired.cause())));
  }

  private CompletableFuture<Result<RequestQueueLock>> acquire(RequestQueueKey key,
    RequestQueueLockConfiguration configuration, long startedAt, int attempt) {

    return repository.tryAcquire(key, configuration.getRequestQueueLockTtlMs())
      .thenCompose(result -> {
        if (result.failed()) {
          return completedFuture(failed(result.cause()));
        }

        Optional<RequestQueueLock> lock = result.value();
        if (lock.isPresent()) {
          log.info("Acquired request queue lock {} for {}:{} after {} attempt(s)",
            lock.get().getId(), key.queueType(), key.queueId(), attempt);
          return completedFuture(succeeded(lock.get()));
        }

        long elapsedMs = elapsedMs(startedAt);
        long delayMs = retryDelay(configuration, attempt);
        if (elapsedMs + delayMs > configuration.getRequestQueueLockMaxWaitMs()) {
          log.warn("Timed out acquiring request queue lock for {}:{} after {} ms",
            key.queueType(), key.queueId(), elapsedMs);
          return completedFuture(failed(new ServiceUnavailableFailure(LOCK_TIMEOUT)));
        }

        CompletableFuture<Result<RequestQueueLock>> retry = new CompletableFuture<>();
        vertx.setTimer(delayMs, ignored -> acquire(key, configuration, startedAt, attempt + 1)
          .whenComplete((retryResult, error) -> complete(retry, retryResult, error)));
        return retry;
      });
  }

  private <T> CompletableFuture<Result<T>> executeAndRelease(RequestQueueKey key,
    RequestQueueLock lock, Supplier<CompletableFuture<Result<T>>> criticalSection,
    long acquisitionWaitMs) {

    long criticalSectionStartedAt = System.nanoTime();
    return invoke(criticalSection)
      .handle(OperationOutcome<T>::new)
      .thenCompose(outcome -> repository.release(lock)
        .handle((releaseResult, releaseError) -> {
          if (releaseError != null) {
            log.error("Failed to release request queue lock {} for {}:{}",
              lock.getId(), key.queueType(), key.queueId(), releaseError);
          } else if (releaseResult.failed()) {
            log.error("Failed to release request queue lock {} for {}:{}: {}",
              lock.getId(), key.queueType(), key.queueId(), releaseResult.cause());
          } else {
            log.info("Released request queue lock {} for {}:{}; wait={} ms, held={} ms",
              lock.getId(), key.queueType(), key.queueId(), acquisitionWaitMs,
              elapsedMs(criticalSectionStartedAt));
          }

          if (outcome.error() != null) {
            throw new CompletionException(outcome.error());
          }
          return outcome.result();
        }));
  }

  private static <T> CompletableFuture<Result<T>> invoke(
    Supplier<CompletableFuture<Result<T>>> operation) {
    try {
      return operation.get();
    } catch (Exception exception) {
      return failedFuture(exception);
    }
  }

  private static long retryDelay(RequestQueueLockConfiguration configuration, int attempt) {
    long baseDelay = Math.min((long) configuration.getRequestQueueLockRetryIntervalMs() * attempt,
      1_000L);
    long jitterBound = Math.max(1L, baseDelay / 4L);
    return baseDelay + ThreadLocalRandom.current().nextLong(jitterBound);
  }

  private static long elapsedMs(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }

  private static <T> void complete(CompletableFuture<T> future, T result, Throwable error) {
    if (error == null) {
      future.complete(result);
    } else {
      future.completeExceptionally(error);
    }
  }

  private record OperationOutcome<T>(Result<T> result, Throwable error) { }
}
