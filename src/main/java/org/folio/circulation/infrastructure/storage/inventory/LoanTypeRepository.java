package org.folio.circulation.infrastructure.storage.inventory;

import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValuesAndCombine;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.LoanTypeMapper;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

public class LoanTypeRepository {
  private static final String LOAN_TYPES = "loantypes";
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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
      LOAN_TYPES, mapper::toDomain)
      .findByIds(ids);
  }

  <T> CompletableFuture<Result<MultipleRecords<T>>> findByIdsAndCombine(Set<String> ids,
    Function<Result<MultipleRecords<LoanType>>, Result<MultipleRecords<T>>> combineFunction) {

    log.debug("findByIdsAndCombine:: parameters instanceIds: {}",
      () -> collectionAsString(ids));

    return findWithMultipleCqlIndexValuesAndCombine(loanTypesClient,
      LOAN_TYPES, new LoanTypeMapper()::toDomain, combineFunction)
      .findByIdsAndCombine(ids);
  }
}
