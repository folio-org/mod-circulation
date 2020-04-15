package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
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

  public CompletableFuture<Result<FeeFineOwner>> findById(String id) {
    if(isNull(id)) {
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<FeeFineOwner>forRecord("feeFineOwner")
      .using(feeFineOwnerStorageClient)
      .mapTo(FeeFineOwner::from)
      .whenNotFound(succeeded(null))
      .fetch(id);
  }

  private Result<MultipleRecords<FeeFineOwner>> mapResponseToOwners(Response response) {
    return MultipleRecords.from(response, FeeFineOwner::from, "owners");
  }

}
