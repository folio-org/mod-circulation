package org.folio.circulation.infrastructure.storage.inventory;

import static org.folio.circulation.domain.representations.ItemProperties.HOLDINGS_RECORD_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.HoldingsMapper;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

public class HoldingsRepository {
  private final CollectionResourceClient holdingsClient;

  public HoldingsRepository(CollectionResourceClient holdingsClient) {
    this.holdingsClient = holdingsClient;
  }

  CompletableFuture<Result<Holdings>> fetchById(String id) {
    final var mapper = new HoldingsMapper();

    return SingleRecordFetcher.json(holdingsClient, "holding",
        r -> failedValidation("Holding does not exist", HOLDINGS_RECORD_ID, id))
      .fetch(id)
      .thenApply(mapResult(mapper::toDomain));
  }

  CompletableFuture<Result<MultipleRecords<Holdings>>> fetchByInstanceId(String instanceId) {
    final var mapper = new HoldingsMapper();

    final var holdingsRecordFetcher = findWithCqlQuery(
      holdingsClient, "holdingsRecords", mapper::toDomain);

    return holdingsRecordFetcher.findByQuery(exactMatch("instanceId", instanceId));
  }

  CompletableFuture<Result<MultipleRecords<Holdings>>> fetchByIds(
    Collection<String> holdingsRecordIds) {

    final var mapper = new HoldingsMapper();

    return findWithMultipleCqlIndexValues(holdingsClient, "holdingsRecords",
        mapper::toDomain)
      .findByIds(holdingsRecordIds);
  }
}
