package org.folio.circulation.infrastructure.storage.feesandfines;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.allOf;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.fetching.CqlQueryFinder;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

public class FeeFineActionRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient feeFineActionsStorageClient;

  public FeeFineActionRepository(Clients clients) {
    feeFineActionsStorageClient = clients.feeFineActionsStorageClient();
  }

  public CompletableFuture<Result<FeeFineAction>> create(StoredFeeFineAction feeFineAction) {

    log.debug("create:: parameters feeFineAction: {}", feeFineAction);
    final ResponseInterpreter<FeeFineAction> interpreter =
      new ResponseInterpreter<FeeFineAction>()
        .flatMapOn(201, mapUsingJson(FeeFineAction::from))
        .otherwise(forwardOnFailure());

    return feeFineActionsStorageClient.post(feeFineAction)
      .thenApply(interpreter::flatMap);
  }

  public CompletableFuture<Result<FeeFineAction>> findById(String id) {

    log.debug("findById:: parameters id: {}", id);
    if (isNull(id)) {
      log.warn("findById:: id is null");
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<FeeFineAction>forRecord("feeFineAction")
      .using(feeFineActionsStorageClient)
      .mapTo(FeeFineAction::from)
      .whenNotFound(failed(new RecordNotFoundFailure("feeFineAction", id)))
      .fetch(id)
      .thenApply(r -> {
        log.info("findById:: result: {}", resultAsString(r));
        return r;
      });
  }

  public CompletableFuture<Result<FeeFineAction>> findChargeActionForAccount(Account account) {

    log.debug("findChargeActionForAccount:: params account: {}", account);

    if (isNull(account)) {
      log.warn("findChargeActionForAccount:: account is null");
      return emptyAsync();
    }

    Result<CqlQuery> query = CqlQuery.exactMatch("accountId", account.getId())
      .combine(exactMatch("typeAction", account.getFeeFineType()), CqlQuery::and);

    return new CqlQueryFinder<>(feeFineActionsStorageClient, "feefineactions", FeeFineAction::from)
      .findByQuery(query, PageLimit.one())
      .thenApply(mapResult(MultipleRecords::firstOrNull));
  }

  public CompletableFuture<Result<Void>> createAll(
    Collection<StoredFeeFineAction> feeFineActions) {

    log.debug("createAll:: parameters feeFineActions: {}", () -> collectionAsString(feeFineActions));

    return allOf(feeFineActions.stream()
      .map(this::create)
      .toArray(CompletableFuture[]::new))
      .thenApply(Result::succeeded)
      .exceptionally(CommonFailures::failedDueToServerError);
  }
}
