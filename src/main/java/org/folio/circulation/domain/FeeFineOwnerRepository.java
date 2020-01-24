package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FeeFineOwnerRepository {
//  private static final Integer PAGE_LIMIT = 500;

  private final CollectionResourceClient feeFineOwnerStorageClient;

  public FeeFineOwnerRepository(Clients clients) {
    feeFineOwnerStorageClient = clients.feeFineOwnerStorageClient();
  }

  public CompletableFuture<Result<FeeFineOwner>> getFeeFineOwner(String servicePoint) {
    return feeFineOwnerStorageClient.get()
      .thenApply(r -> r.next(this::mapResponseToOwners))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(owners -> owners.stream()
        .filter(owner -> owner.getServicePoints().stream().anyMatch(servicePoint::equals))
        .findAny().orElse(null)));
  }

  private Result<MultipleRecords<FeeFineOwner>> mapResponseToOwners(Response response) {
    return MultipleRecords.from(response, FeeFineOwner::from, "owners");
  }
//
//  private MultipleRecordFetcher<FeeFineOwner> createFeeFineOwnerStorageClient() {
//    return new MultipleRecordFetcher<>(feeFineOwnerStorageClient, "owners", FeeFineOwner::from);
//  }
}
