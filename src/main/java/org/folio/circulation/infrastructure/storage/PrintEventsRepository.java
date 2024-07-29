package org.folio.circulation.infrastructure.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.PrintEventRequest;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.results.Result.succeeded;

public class PrintEventsRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient printEventsStorageClient;

  public PrintEventsRepository(Clients clients) {
    printEventsStorageClient = clients.printEventsStorageClient();
  }

  public CompletableFuture<Result<Void>> create(PrintEventRequest printEventRequest) {
    log.info("create:: parameters printEvent: {}", printEventRequest);
    final var storagePrintEventRequest = printEventRequest.getRepresentation();
    final ResponseInterpreter<Void> interpreter = new ResponseInterpreter<Void>()
      .on(201, succeeded(null))
      .otherwise(forwardOnFailure());
    return printEventsStorageClient.post(storagePrintEventRequest).thenApply(interpreter::flatMap);
  }

}
