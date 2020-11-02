package org.folio.circulation.support.fetching;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

public interface GetManyRecordsRepository<T> {
  CompletableFuture<Result<MultipleRecords<T>>> getMany(CqlQuery cqlQuery,
    PageLimit pageLimit, Offset offset);
}
