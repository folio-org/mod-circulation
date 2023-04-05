package org.folio.circulation.infrastructure.storage.feesandfines;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.allOf;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

public class FeeFineActionRepository {
  private final CollectionResourceClient feeFineActionsStorageClient;
  private final FindWithMultipleCqlIndexValues<FeeFineAction> feeFineActionFetcher;
  public static final String FEE_FINE_ID = "feeFineId";

  public FeeFineActionRepository(Clients clients) {
    feeFineActionsStorageClient = clients.feeFineActionsStorageClient();
    feeFineActionFetcher = findWithMultipleCqlIndexValues(clients.feeFineActionsStorageClient(),
      "feeFineActions", FeeFineAction::from);
  }

  public CompletableFuture<Result<FeeFineAction>> create(StoredFeeFineAction feeFineAction) {
    final ResponseInterpreter<FeeFineAction> interpreter =
      new ResponseInterpreter<FeeFineAction>()
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
      .whenNotFound(failed(new RecordNotFoundFailure("feeFineAction", id)))
      .fetch(id);
  }

  public CompletableFuture<Result<Void>> createAll(
    Collection<StoredFeeFineAction> feeFineActions) {

    return allOf(feeFineActions.stream()
      .map(this::create)
      .toArray(CompletableFuture[]::new))
      .thenApply(Result::succeeded)
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  public CompletableFuture<Result<Collection<FeeFineAction>>> findByFeeFineId(
    String feeFineId) {

    if(isNull(feeFineId)) {
      return ofAsync(() -> null);
    }

    return feeFineActionFetcher.find(MultipleCqlIndexValuesCriteria.builder()
      .indexName(FEE_FINE_ID)
      .indexOperator((index, value) -> CqlQuery.exactMatch(index, feeFineId))
      .build())
      .thenApply(mapResult(MultipleRecords::getRecords));
  }
}
