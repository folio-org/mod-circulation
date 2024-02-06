package org.folio.circulation.infrastructure.storage.inventory;

import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValuesAndCombine;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.LoanTypeMapper;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

public class LoanTypeRepository {
  public final CollectionResourceClient loanTypesClient;

  public LoanTypeRepository(CollectionResourceClient loanTypesClient) {
    this.loanTypesClient = loanTypesClient;
  }

  public CompletableFuture<Result<LoanType>> fetchById(String id) {
    final var mapper = new LoanTypeMapper();

    return SingleRecordFetcher.json(loanTypesClient,
        "loan types",
        response -> succeeded(null))
      .fetch(id)
      .thenApply(mapResult(mapper::toDomain));
  }

  CompletableFuture<Result<MultipleRecords<LoanType>>> findByIds(Set<String> ids) {
    final var mapper = new LoanTypeMapper();

    return findWithMultipleCqlIndexValues(loanTypesClient,
      "loantypes", mapper::toDomain)
      .findByIds(ids);
  }

  CompletableFuture<Result<MultipleRecords<Item>>> findByIdsAndCombine(Set<String> ids,
    Function<Result<MultipleRecords<LoanType>>, Result<MultipleRecords<Item>>> combiner) {

    final var mapper = new LoanTypeMapper();

    return findWithMultipleCqlIndexValuesAndCombine(loanTypesClient,
      "loantypes", mapper::toDomain, combiner)
      .findByIdsAndCombine(ids);
  }
}
