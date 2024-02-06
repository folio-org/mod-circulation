package org.folio.circulation.support;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.results.Result;

public interface CombineWithMultipleCqlIndexValues<T, R> {
  CompletableFuture<Result<MultipleRecords<R>>> findByIdsAndCombine(Collection<String> ids);
}
