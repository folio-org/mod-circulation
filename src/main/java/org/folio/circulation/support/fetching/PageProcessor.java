package org.folio.circulation.support.fetching;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.results.Result;

@FunctionalInterface
public interface PageProcessor<T> {
  CompletableFuture<Result<Void>> processPage(MultipleRecords<T> records);
}
