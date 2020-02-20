package org.folio.circulation.support;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;

public interface FindWithCqlQuery<T> {
    CompletableFuture<Result<MultipleRecords<T>>> findByQuery(
      Result<CqlQuery> queryResult);

  CompletableFuture<Result<MultipleRecords<T>>> findByQuery(
    Result<CqlQuery> queryResult, PageLimit pageLimit);
}
