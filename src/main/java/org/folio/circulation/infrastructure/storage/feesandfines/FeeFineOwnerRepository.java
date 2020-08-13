package org.folio.circulation.infrastructure.storage.feesandfines;

import static org.folio.circulation.support.http.client.PageLimit.one;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.CollectionUtil;

public class FeeFineOwnerRepository {
  public static final String SERVICE_POINT_OWNER_KEY = "servicePointOwner";

  private final CollectionResourceClient feeFineOwnerStorageClient;

  public FeeFineOwnerRepository(Clients clients) {
    feeFineOwnerStorageClient = clients.feeFineOwnerStorageClient();
  }

  public CompletableFuture<Result<FeeFineOwner>> findOwnerForServicePoint(String servicePointId) {
    return CqlQuery.match(SERVICE_POINT_OWNER_KEY, servicePointId)
      .after(query -> feeFineOwnerStorageClient.getMany(query, one()))
      .thenApply(r -> r.next(this::mapResponseToOwners))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(CollectionUtil::firstOrNull));
  }

  private Result<MultipleRecords<FeeFineOwner>> mapResponseToOwners(Response response) {
    return MultipleRecords.from(response, FeeFineOwner::from, "owners");
  }
}
