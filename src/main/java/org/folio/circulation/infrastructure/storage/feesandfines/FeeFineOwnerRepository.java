package org.folio.circulation.infrastructure.storage.feesandfines;

import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

public class FeeFineOwnerRepository {
  public static final String SERVICE_POINT_OWNER_KEY = "servicePointOwner";

  private final FindWithMultipleCqlIndexValues<FeeFineOwner> feeFineOwnersFetcher;

  public FeeFineOwnerRepository(Clients clients) {
    feeFineOwnersFetcher = findWithMultipleCqlIndexValues(clients.feeFineOwnerStorageClient(),
      "owners", FeeFineOwner::from);
  }

  public CompletableFuture<Result<FeeFineOwner>> findOwnerForServicePoint(String servicePointId) {
    return findOwners(Collections.singleton(servicePointId))
      .thenApply(mapResult(MultipleRecords::firstOrNull));
  }

  public CompletableFuture<Result<FeeFineOwner>> findOwnerForServicePoint(UUID servicePointId) {
    return findOwnerForServicePoint(servicePointId.toString());
  }

  public CompletableFuture<Result<Collection<FeeFineOwner>>> findOwnersForServicePoints(
    Collection<String> servicePointIds) {

    return findOwners(servicePointIds)
      .thenApply(mapResult(MultipleRecords::getRecords));
  }

  private CompletableFuture<Result<MultipleRecords<FeeFineOwner>>> findOwners(
    Collection<String> servicePointIds) {

    return feeFineOwnersFetcher.find(MultipleCqlIndexValuesCriteria.builder()
      .indexName(SERVICE_POINT_OWNER_KEY)
      .indexOperator(CqlQuery::matchAny)
      .values(servicePointIds)
      .build());
  }
}
