package org.folio.circulation.infrastructure.storage;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.Environment;
import org.folio.circulation.domain.CheckOutLock;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

public class CheckOutLockRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient checkOutLockClient;

  public CheckOutLockRepository(Clients clients) {
    this.checkOutLockClient = clients.checkOutLockClient();
  }

  public CompletableFuture<Result<CheckOutLock>> create(LoanAndRelatedRecords records) {
    log.info("create:: trying to create lock");
    final ResponseInterpreter<CheckOutLock> interpreter =
      new ResponseInterpreter<CheckOutLock>()
        .flatMapOn(201, mapUsingJson(CheckOutLock::from))
        .otherwise(forwardOnFailure());

    return checkOutLockClient.post(buildCheckOutLockPayload(records))
      .thenApply(interpreter::flatMap);

  }

  private JsonObject buildCheckOutLockPayload(LoanAndRelatedRecords records) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("userId", records.getUserId());
    jsonObject.put("ttl", Environment.getLockTTL());
    return jsonObject;
  }
}
