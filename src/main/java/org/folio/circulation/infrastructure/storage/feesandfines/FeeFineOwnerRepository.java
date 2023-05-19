package org.folio.circulation.infrastructure.storage.feesandfines;

import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

public class FeeFineOwnerRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public static final String SERVICE_POINT_OWNER_KEY = "servicePointOwner";

  private final FindWithMultipleCqlIndexValues<FeeFineOwner> feeFineOwnersFetcher;

  public FeeFineOwnerRepository(Clients clients) {
    feeFineOwnersFetcher = findWithMultipleCqlIndexValues(clients.feeFineOwnerStorageClient(),
      "owners", FeeFineOwner::from);
  }

  public CompletableFuture<Result<FeeFineOwner>> findOwnerForServicePoint(String servicePointId) {
    log.debug("findOwnerForServicePoint:: parameters servicePointId: {}", servicePointId);
    return findOwners(Collections.singleton(servicePointId))
      .thenApply(mapResult(MultipleRecords::firstOrNull));
  }

  public CompletableFuture<Result<FeeFineOwner>> findOwnerForServicePoint(UUID servicePointId) {
    log.debug("findOwnerForServicePoint:: parameters servicePointId: {}", servicePointId);
    return findOwnerForServicePoint(servicePointId.toString());
  }

  public CompletableFuture<Result<Collection<FeeFineOwner>>> findOwnersForServicePoints(
    Collection<String> servicePointIds) {
    log.debug("findOwnersForServicePoints:: parameters servicePointIds: {}", () -> collectionAsString(servicePointIds));

    return findOwners(servicePointIds)
      .thenApply(mapResult(MultipleRecords::getRecords));
  }

  private CompletableFuture<Result<MultipleRecords<FeeFineOwner>>> findOwners(
    Collection<String> servicePointIds) {
    log.debug("findOwners:: parameters servicePointIds: {}", () -> collectionAsString(servicePointIds));

    return feeFineOwnersFetcher.find(MultipleCqlIndexValuesCriteria.builder()
      .indexName(SERVICE_POINT_OWNER_KEY)
      .indexOperator(CqlQuery::matchAny)
      .values(servicePointIds)
      .build());
  }
}
