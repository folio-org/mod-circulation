package org.folio.circulation.infrastructure.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.RequestQueueKey.QueueType.INSTANCE;
import static org.folio.circulation.domain.RequestQueueKey.QueueType.ITEM;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.RequestQueueKey;
import org.folio.circulation.domain.RequestQueueLock;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.json.JsonObject;

class RequestQueueLockRepositoryTest {
  private static final int TIMEOUT_SECONDS = 2;

  private CollectionResourceClient client;
  private RequestQueueLockRepository repository;

  @BeforeEach
  void setUp() {
    Clients clients = mock(Clients.class);
    client = mock(CollectionResourceClient.class);
    when(clients.requestQueueLockStorageClient()).thenReturn(client);
    repository = new RequestQueueLockRepository(clients);
  }

  @Test
  void acquirePostsEffectiveQueueIdentityAndTtl() throws Exception {
    String lockId = UUID.randomUUID().toString();
    RequestQueueKey key = new RequestQueueKey(INSTANCE, UUID.randomUUID().toString());
    when(client.post(any(JsonObject.class))).thenReturn(completedFuture(succeeded(
      new Response(201, new JsonObject().put("id", lockId).encode(), "application/json"))));

    Result<Optional<RequestQueueLock>> result = repository.tryAcquire(key, 12_345)
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    assertThat(result.value().orElseThrow().getId(), is(lockId));
    ArgumentCaptor<JsonObject> body = ArgumentCaptor.forClass(JsonObject.class);
    verify(client).post(body.capture());
    assertThat(body.getValue().getString("queueType"), is("INSTANCE"));
    assertThat(body.getValue().getString("queueId"), is(key.queueId()));
    assertThat(body.getValue().getInteger("ttlMs"), is(12_345));
  }

  @Test
  void conflictMeansQueueIsCurrentlyLocked() throws Exception {
    RequestQueueKey key = new RequestQueueKey(ITEM, UUID.randomUUID().toString());
    when(client.post(any(JsonObject.class))).thenReturn(completedFuture(succeeded(
      new Response(409, "Request queue is already locked", "text/plain"))));

    Result<Optional<RequestQueueLock>> result = repository.tryAcquire(key, 10_000)
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(Optional.empty()));
  }

  @Test
  void unexpectedAcquireResponseIsForwardedAsFailure() throws Exception {
    RequestQueueKey key = new RequestQueueKey(ITEM, UUID.randomUUID().toString());
    when(client.post(any(JsonObject.class))).thenReturn(completedFuture(succeeded(
      new Response(500, "Storage failure", "text/plain"))));

    Result<Optional<RequestQueueLock>> result = repository.tryAcquire(key, 10_000)
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), is(notNullValue()));
  }

  @Test
  void releaseDeletesLockById() throws Exception {
    String lockId = UUID.randomUUID().toString();
    RequestQueueLock lock = RequestQueueLock.from(new JsonObject().put("id", lockId));
    when(client.delete(lockId)).thenReturn(completedFuture(succeeded(
      new Response(204, null, null))));

    Result<Response> result = repository.release(lock)
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    verify(client).delete(lockId);
  }
}
