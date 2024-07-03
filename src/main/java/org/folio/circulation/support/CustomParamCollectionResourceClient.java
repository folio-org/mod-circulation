package org.folio.circulation.support;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.QueryParameter;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

public class CustomParamCollectionResourceClient extends CollectionResourceClient {

  private QueryParameter customQueryParameter;

  public CustomParamCollectionResourceClient(OkapiHttpClient client, URL collectionRoot,
    QueryParameter customQueryParameter) {

    super(client, collectionRoot);
    this.customQueryParameter = customQueryParameter;
  }

  @Override
  public CompletableFuture<Result<Response>> get() {
    return client.get(collectionRoot.toString(), customQueryParameter);
  }

  @Override
  public CompletableFuture<Result<Response>> get(PageLimit pageLimit) {
    return client.get(collectionRoot, pageLimit, customQueryParameter);
  }

  @Override
  public CompletableFuture<Result<Response>> get(String id) {
    return client.get(individualRecordUrl(id), customQueryParameter);
  }

  @Override
  public CompletableFuture<Result<Response>> getMany(CqlQuery cqlQuery,
    PageLimit pageLimit, Offset offset) {

    return client.get(collectionRoot, cqlQuery, pageLimit, offset, customQueryParameter);
  }
}
