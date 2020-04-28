package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.representations.FeeFineActionStorageRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.ResponseInterpreter;

public class FeeFineActionRepository {
  private final CollectionResourceClient feeFineActionsStorageClient;

  public FeeFineActionRepository(Clients clients) {
    feeFineActionsStorageClient = clients.feeFineActionsStorageClient();
  }

  public CompletableFuture<Result<FeeFineAction>> create(
    FeeFineActionStorageRepresentation feeFineAction) {

    final ResponseInterpreter<FeeFineAction> interpreter = new ResponseInterpreter<FeeFineAction>()
      .flatMapOn(201, mapUsingJson(FeeFineAction::from))
      .otherwise(forwardOnFailure());

    return feeFineActionsStorageClient.post(feeFineAction)
      .thenApply(interpreter::flatMap);
  }

  public CompletableFuture<Result<FeeFineAction>> findById(String id) {
    if(isNull(id)) {
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<FeeFineAction>forRecord("feeFineAction")
      .using(feeFineActionsStorageClient)
      .mapTo(FeeFineAction::from)
      .whenNotFound(succeeded(null))
      .fetch(id);
  }

}
