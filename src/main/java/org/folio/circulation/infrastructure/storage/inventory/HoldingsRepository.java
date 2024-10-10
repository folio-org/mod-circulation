package org.folio.circulation.infrastructure.storage.inventory;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.HoldingsMapper;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class HoldingsRepository {
  private static final String HOLDINGS_RECORDS = "holdingsRecords";
  private final CollectionResourceClient holdingsClient;

  public HoldingsRepository(CollectionResourceClient holdingsClient) {
    this.holdingsClient = holdingsClient;
  }

  CompletableFuture<Result<Holdings>> fetchById(String id) {
    final var mapper = new HoldingsMapper();

    return fetchAsJson(id)
      .thenApply(mapResult(mapper::toDomain));
  }

  public CompletableFuture<Result<JsonObject>> fetchAsJson(String id) {
    return SingleRecordFetcher.json(holdingsClient, "holdings",
        r -> failedValidation("Holdings record does not exist", "id", id))
      .fetch(id);
  }

  CompletableFuture<Result<MultipleRecords<Holdings>>> fetchByInstanceId(String instanceId) {
    final var mapper = new HoldingsMapper();

    final var holdingsRecordFetcher = findWithCqlQuery(
      holdingsClient, HOLDINGS_RECORDS, mapper::toDomain);

    return holdingsRecordFetcher.findByQuery(exactMatch("instanceId", instanceId));
  }

  public CompletableFuture<Result<MultipleRecords<Holdings>>> fetchByInstances(
    Collection<String> instanceIds) {

    final var mapper = new HoldingsMapper();
    final var holdingsRecordFetcher = findWithCqlQuery(
      holdingsClient, HOLDINGS_RECORDS, mapper::toDomain);

    return holdingsRecordFetcher.findByQuery(exactMatchAny("instanceId",
      instanceIds));
  }

  CompletableFuture<Result<MultipleRecords<Holdings>>> fetchByIds(
    Collection<String> holdingsRecordIds) {

    final var mapper = new HoldingsMapper();

    return findWithMultipleCqlIndexValues(holdingsClient, HOLDINGS_RECORDS,
        mapper::toDomain)
      .findByIds(holdingsRecordIds);
  }
}
