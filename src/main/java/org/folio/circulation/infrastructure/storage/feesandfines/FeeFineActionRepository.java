package org.folio.circulation.infrastructure.storage.feesandfines;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.fetching.CqlIndexValuesFinder;
import org.folio.circulation.support.fetching.CqlQueryFinder;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

public class FeeFineActionRepository {
  private final CollectionResourceClient feeFineActionsStorageClient;

  public FeeFineActionRepository(Clients clients) {
    feeFineActionsStorageClient = clients.feeFineActionsStorageClient();
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

  public CompletableFuture<Result<FeeFineAction>> findChargeActionForAccount(Account account) {
    if (isNull(account)) {
      return ofAsync(() -> null);
    }

    Result<CqlQuery> query = CqlQuery.exactMatch("accountId", account.getId())
      .combine(exactMatch("typeAction", account.getFeeFineType()), CqlQuery::and);

    return new CqlQueryFinder<>(feeFineActionsStorageClient, "feeFineAction", identity())
      .findByQuery(query, PageLimit.one())
      .thenApply(mapResult(records -> records.mapRecords(FeeFineAction::from)))
      .thenApply(mapResult(MultipleRecords::getRecords))
      .thenApply(mapResult(records -> records.stream().findFirst().orElse(null)));
  }

  public CompletableFuture<Result<Void>> createAll(
    Collection<StoredFeeFineAction> feeFineActions) {

    return allOf(feeFineActions.stream()
      .map(this::create)
      .toArray(CompletableFuture[]::new))
      .thenApply(Result::succeeded)
      .exceptionally(CommonFailures::failedDueToServerError);
  }
}
