package org.folio.circulation.services;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.RequestQueueKey.QueueType.ITEM;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.folio.circulation.domain.RequestQueueKey;
import org.folio.circulation.domain.RequestQueueLock;
import org.folio.circulation.domain.configuration.RequestQueueLockConfiguration;
import org.folio.circulation.infrastructure.storage.RequestQueueLockRepository;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ServiceUnavailableFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class RequestQueueLockServiceTest {
  private static final int TIMEOUT_SECONDS = 2;

  @Mock
  private RequestQueueLockRepository repository;

  private Vertx vertx;
  private RequestQueueLockService service;
  private RequestQueueKey key;
  private RequestQueueLock lock;
  private RequestQueueLockConfiguration configuration;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    service = new RequestQueueLockService(repository, vertx);
    key = new RequestQueueKey(ITEM, UUID.randomUUID().toString());
    lock = RequestQueueLock.from(new JsonObject().put("id", UUID.randomUUID().toString()));
    configuration = new RequestQueueLockConfiguration(true, 10_000, 1, 500);
    lenient().when(repository.release(lock)).thenReturn(completedFuture(succeeded(
      new Response(204, null, null))));
  }

  @AfterEach
  void tearDown() throws Exception {
    vertx.close().toCompletionStage().toCompletableFuture()
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  @Test
  void retriesContentionAndRunsCriticalSectionAfterAcquisition() throws Exception {
    when(repository.tryAcquire(any(), anyInt()))
      .thenReturn(completedFuture(succeeded(empty())))
      .thenReturn(completedFuture(succeeded(of(lock))));

    Result<String> result = service.execute(key, configuration,
        () -> completedFuture(succeeded("created")))
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.value(), is("created"));
    verify(repository, times(2)).tryAcquire(key, configuration.getRequestQueueLockTtlMs());
    verify(repository).release(lock);
  }

  @Test
  void releasesLockWhenCriticalSectionReturnsFailure() throws Exception {
    when(repository.tryAcquire(any(), anyInt()))
      .thenReturn(completedFuture(succeeded(of(lock))));

    Result<String> result = service.execute(key, configuration,
        () -> completedFuture(Result.<String>failed(new ServerErrorFailure("create failed"))))
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.failed(), is(true));
    verify(repository).release(lock);
  }

  @Test
  void releasesLockWhenCriticalSectionCompletesExceptionally() {
    when(repository.tryAcquire(any(), anyInt()))
      .thenReturn(completedFuture(succeeded(of(lock))));

    CompletableFuture<Result<String>> future = service.execute(key, configuration,
      () -> CompletableFuture.failedFuture(new IllegalStateException("create failed")));

    assertThrows(ExecutionException.class,
      () -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    verify(repository).release(lock);
  }

  @Test
  void timesOutWithoutRunningCriticalSection() throws Exception {
    RequestQueueLockConfiguration shortWait =
      new RequestQueueLockConfiguration(true, 10_000, 5, 1);
    AtomicBoolean criticalSectionCalled = new AtomicBoolean();
    when(repository.tryAcquire(any(), anyInt()))
      .thenReturn(completedFuture(succeeded(empty())));

    Result<String> result = service.execute(key, shortWait, () -> {
      criticalSectionCalled.set(true);
      return completedFuture(succeeded("created"));
    }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.failed(), is(true));
    assertThat(result.cause() instanceof ServiceUnavailableFailure, is(true));
    assertThat(criticalSectionCalled.get(), is(false));
    verify(repository, never()).release(any());
  }
}
