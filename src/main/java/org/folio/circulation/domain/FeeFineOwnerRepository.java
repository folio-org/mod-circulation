package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Response;

public class FeeFineOwnerRepository {
  public static final String QUERY_PREFIX = "query=";
  public static final String SERVICE_POINT_OWNER_KEY = "servicePointOwner";
  public static final String VALUE_KEY = "value";

  private final CollectionResourceClient feeFineOwnerStorageClient;

  public FeeFineOwnerRepository(Clients clients) {
    feeFineOwnerStorageClient = clients.feeFineOwnerStorageClient();
  }

  public CompletableFuture<Result<FeeFineOwner>> findOwnerForServicePoint(String servicePointId) {
    return CqlQuery.exactMatchWithinArray(SERVICE_POINT_OWNER_KEY, VALUE_KEY, servicePointId)
      .next(CqlQuery::encode)
      .next(query -> succeeded(QUERY_PREFIX + query))
      .after(feeFineOwnerStorageClient::getManyWithRawQueryStringParameters)
      .thenApply(r -> r.next(this::mapResponseToOwners))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(owners -> owners.stream().findFirst().orElse(null)));
  }

  private Result<MultipleRecords<FeeFineOwner>> mapResponseToOwners(Response response) {
    return MultipleRecords.from(response, FeeFineOwner::from, "owners");
  }

}
