package org.folio.circulation.infrastructure.storage.feesandfines;

import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.CollectionUtil;

public class FeeFineOwnerRepository {
  public static final String SERVICE_POINT_OWNER_KEY = "servicePointOwner";

  private final FindWithMultipleCqlIndexValues<FeeFineOwner> feeFineOwnersFetcher;

  public FeeFineOwnerRepository(Clients clients) {
    feeFineOwnersFetcher = findWithMultipleCqlIndexValues(clients.feeFineOwnerStorageClient(),
      "owners", FeeFineOwner::from);
  }

  public CompletableFuture<Result<FeeFineOwner>> findOwnerForServicePoint(String servicePointId) {
    return findOwnersForServicePoints(Collections.singleton(servicePointId))
      .thenApply(r -> r.map(CollectionUtil::firstOrNull));
  }

  public CompletableFuture<Result<Collection<FeeFineOwner>>> findOwnersForServicePoints(
    Collection<String> servicePointIds) {

    return feeFineOwnersFetcher.find(MultipleCqlIndexValuesCriteria.builder()
      .indexName(SERVICE_POINT_OWNER_KEY)
      .indexOperator(CqlQuery::matchAny)
      .values(servicePointIds)
      .build())
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }
}
