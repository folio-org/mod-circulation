package org.folio.circulation.infrastructure.storage;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.Environment;
import org.folio.circulation.domain.CheckOutLock;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

public class CheckOutLockRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient checkOutLockClient;
  private final List<Integer> retryIntervals;
  private final Vertx vertx=Vertx.vertx();

  public CheckOutLockRepository(Clients clients, List<Integer> retryIntervals) {
    this.checkOutLockClient = clients.checkOutLockClient();
    this.retryIntervals = retryIntervals;
  }

  public void createLockWithRetry(int noOfAttempts, CompletableFuture<CheckOutLock> future, LoanAndRelatedRecords records) {
    log.debug("createLockWithRetry:: Retrying lock creation {} ", noOfAttempts);
    int maxRetry = retryIntervals.size() - 1;
    try {
      create(records)
        .whenComplete((res, err) -> {
          if (res.succeeded()) {
            log.info("createLockWithRetry:: checkOutLock object {} ", res.value());
            future.complete(res.value());
          } else {
            if (noOfAttempts <= maxRetry) {
              log.info("createLockWithRetry:: Retry attempt {} for lock creation with delay {}", noOfAttempts, retryIntervals.get(noOfAttempts));
              vertx.setTimer(retryIntervals.get(noOfAttempts), h -> createLockWithRetry(noOfAttempts + 1, future, records));
            } else {
              String error = res.cause() != null ? res.cause().toString() : "";
              log.warn("createLockWithRetry:: Completing exceptionally {} ", error);
              future.completeExceptionally(new RuntimeException(error));
            }
          }
        });
    } catch (Exception ex) {
      log.warn("createLockWithRetry:: exception {} ", ex.getMessage());
      future.completeExceptionally(ex);
    }
  }


  public CompletableFuture<Result<CheckOutLock>> create(LoanAndRelatedRecords records) {
    log.debug("create:: trying to create lock for userId {} ", records.getUserId());
    final ResponseInterpreter<CheckOutLock> interpreter =
      new ResponseInterpreter<CheckOutLock>()
        .flatMapOn(201, mapUsingJson(CheckOutLock::from))
        .otherwise(forwardOnFailure());

    return checkOutLockClient.post(buildCheckOutLockPayload(records))
      .thenApply(interpreter::flatMap);

  }

  public CompletableFuture<Result<Response>> delete(String checkOutLockId) {
    log.debug("delete:: deleting the lock for userId {} ", checkOutLockId);
    return checkOutLockClient.delete(checkOutLockId);
  }

  private JsonObject buildCheckOutLockPayload(LoanAndRelatedRecords records) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("userId", records.getUserId());
    jsonObject.put("ttl", Environment.getLockTTL());
    return jsonObject;
  }

}
