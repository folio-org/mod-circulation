package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.RequestQueueKey;
import org.folio.circulation.domain.RequestQueueLock;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class RequestQueueLockRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient client;

  public RequestQueueLockRepository(Clients clients) {
    this.client = clients.requestQueueLockStorageClient();
  }

  public CompletableFuture<Result<Optional<RequestQueueLock>>> tryAcquire(
    RequestQueueKey key, int ttlMs) {

    log.debug("Trying to acquire request queue lock for {}:{}", key.queueType(), key.queueId());
    ResponseInterpreter<Optional<RequestQueueLock>> interpreter =
      new ResponseInterpreter<Optional<RequestQueueLock>>()
        .flatMapOn(201, response -> mapUsingJson(RequestQueueLock::from).apply(response)
          .map(Optional::of))
        .on(409, succeeded(Optional.empty()))
        .otherwise(forwardOnFailure());

    return client.post(new JsonObject()
        .put("queueType", key.queueType().name())
        .put("queueId", key.queueId())
        .put("ttlMs", ttlMs))
      .thenApply(interpreter::flatMap);
  }

  public CompletableFuture<Result<Response>> release(RequestQueueLock lock) {
    log.debug("Releasing request queue lock {}", lock.getId());
    ResponseInterpreter<Response> interpreter = new ResponseInterpreter<Response>()
      .flatMapOn(204, succeededResponse -> succeeded(succeededResponse))
      .otherwise(forwardOnFailure());

    return client.delete(lock.getId()).thenApply(interpreter::flatMap);
  }
}
