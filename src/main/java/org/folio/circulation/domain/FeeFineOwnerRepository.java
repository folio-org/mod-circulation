package org.folio.circulation.domain;


import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;

public class FeeFineOwnerRepository {
  public static final PageLimit OWNERS_PAGE_LIMIT = PageLimit.limit(200);
  public static final String SERVICE_POINT_OWNER_KEY = "servicePointOwner";
  public static final String EMPTY_ARRAY = "[]";

  private final CollectionResourceClient feeFineOwnerStorageClient;

  public FeeFineOwnerRepository(Clients clients) {
    feeFineOwnerStorageClient = clients.feeFineOwnerStorageClient();
  }

  public CompletableFuture<Result<FeeFineOwner>> findOwnerForServicePoint(String servicePointId) {
    return CqlQuery.notEqual(SERVICE_POINT_OWNER_KEY, EMPTY_ARRAY)
      .after(query -> feeFineOwnerStorageClient.getMany(query, OWNERS_PAGE_LIMIT))
      .thenApply(r -> r.next(this::mapResponseToOwners))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(owners -> owners.stream()
        .filter(owner -> owner.getServicePoints().stream().anyMatch(servicePointId::equals))
        .findAny()
        .orElse(null))
      );
  }

  private Result<MultipleRecords<FeeFineOwner>> mapResponseToOwners(Response response) {
    return MultipleRecords.from(response, FeeFineOwner::from, "owners");
  }

}
