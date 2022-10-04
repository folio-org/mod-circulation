package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Identifier;
import org.folio.circulation.domain.IdentifierType;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.IdentityMap;
import org.folio.circulation.storage.mappers.IdentifierTypeMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.fetching.CqlIndexValuesFinder;
import org.folio.circulation.support.fetching.CqlQueryFinder;
import org.folio.circulation.support.results.Result;

public class IdentifierTypeRepository {
  private final CollectionResourceClient identifierTypeClient;

  public IdentifierTypeRepository(CollectionResourceClient identifierTypeClient) {
    this.identifierTypeClient = identifierTypeClient;
  }

  public IdentifierTypeRepository(Clients clients) {
    this(clients.identifierTypesStorage());
  }

  private final IdentityMap identityMap = new IdentityMap(
    item -> getProperty(item, "id"));

  public CompletableFuture<Result<Collection<IdentifierType>>> fetchFor(Item item) {
    if (item == null || item.getIdentifiers() == null) {
      return completedFuture(succeeded(List.of()));
    }

    final var finder = new CqlIndexValuesFinder<>(new CqlQueryFinder<>(identifierTypeClient,
      "identifierTypes", identity()));
    final var mapper = new IdentifierTypeMapper();

    return finder.findByIds(item.getIdentifiers()
        .map(Identifier::getIdentifierTypeId)
        .collect(Collectors.toList()))
      .thenApply(mapResult(identityMap::add))
      .thenApply(mapResult(records -> records.mapRecords(mapper::toDomain)))
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }
}
