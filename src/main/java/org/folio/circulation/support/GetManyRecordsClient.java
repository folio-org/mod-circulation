package org.folio.circulation.support;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;

public interface GetManyRecordsClient {
    CompletableFuture<Result<Response>> getMany(CqlQuery cqlQuery,
      PageLimit pageLimit);

    CompletableFuture<Result<Response>> getMany(CqlQuery cqlQuery,
      PageLimit pageLimit, Offset offset);
}
