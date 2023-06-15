package org.folio.circulation.infrastructure.storage.feesandfines;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;

public class FeeFineRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient feeFineStorageClient;

  public FeeFineRepository(Clients clients) {
    feeFineStorageClient = clients.feeFineStorageClient();
  }

  public CompletableFuture<Result<FeeFine>> getFeeFine(String type, boolean automatic) {
    log.debug("getFeeFine:: parameters type: {}, automatic: {}", type, automatic);
    return getFeeFines(Collections.singleton(type), automatic)
      .thenApply(r -> r.map(col -> col.stream().findFirst().orElse(null)));
  }

  private CompletableFuture<Result<Collection<FeeFine>>> getFeeFines(
    Collection<String> types, boolean automatic) {

    final Result<CqlQuery> typeQuery = CqlQuery.exactMatchAny("feeFineType", types);
    final Result<CqlQuery> automaticQuery = CqlQuery.exactMatch("automatic", Boolean.toString(automatic));

    return typeQuery.combine(automaticQuery, CqlQuery::and)
      .after(q -> feeFineStorageClient.getMany(q, limit(types.size())))
      .thenApply(r -> r.next(this::mapResponseToFeeFines))
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<Collection<FeeFine>>> getAutomaticFeeFines(Collection<String> types) {
    log.debug("getAutomaticFeeFines:: parameters types: {}", () -> collectionAsString(types));
    return getFeeFines(types, true);
  }

  private Result<MultipleRecords<FeeFine>> mapResponseToFeeFines(Response response) {
    log.debug("mapResponseToFeeFines:: response body: {}", response::getBody);
    return MultipleRecords.from(response, FeeFine::from, "feefines");
  }

  public CompletableFuture<Result<FeeFine>> create(FeeFine feeFine) {
    log.debug("create:: parameters feeFine: {}", feeFine);
    final ResponseInterpreter<FeeFine> interpreter = new ResponseInterpreter<FeeFine>()
      .flatMapOn(201, mapUsingJson(FeeFine::from))
      .otherwise(forwardOnFailure());

    return feeFineStorageClient.post(feeFine.toJson())
      .thenApply(interpreter::flatMap);
  }
}
