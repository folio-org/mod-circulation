package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;

public class FeeFineRepository {
  private static final Integer PAGE_LIMIT = 500;

  private final CollectionResourceClient feeFineStorageClient;

  public FeeFineRepository(Clients clients) {
    feeFineStorageClient = clients.feeFineStorageClient();
  }

  public CompletableFuture<Result<FeeFine>> getFeeFine(String feeFineOwnerId, String feeFineType) {
    Result<CqlQuery> typeQuery = CqlQuery.exactMatch("feeFineType", feeFineType);
    Result<CqlQuery> ownerQuery = CqlQuery.exactMatch("ownerId", feeFineOwnerId);

    return typeQuery.combine(ownerQuery, CqlQuery::and)
      .after(q -> feeFineStorageClient.getMany(q, PageLimit.limit(PAGE_LIMIT)))
      .thenApply(r -> r.next(this::mapResponseToFeefines))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(col -> col.stream().findAny().orElse(null)));
  }

  private Result<MultipleRecords<FeeFine>> mapResponseToFeefines(Response response) {
    return MultipleRecords.from(response, FeeFine::from, "feefines");
  }

  private MultipleRecordFetcher<FeeFine> createFeeFineStorageClient() {
    return new MultipleRecordFetcher<>(feeFineStorageClient, "feefines", FeeFine::from);
  }
}
