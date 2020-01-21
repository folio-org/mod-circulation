package org.folio.circulation.support;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.circulation.support.http.client.Offset.noOffset;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class CollectionResourceClient {
  private final OkapiHttpClient client;
  private final URL collectionRoot;

  public CollectionResourceClient(OkapiHttpClient client, URL collectionRoot) {
    this.collectionRoot = collectionRoot;
    this.client = client;
  }

  public CompletableFuture<Result<Response>> post(JsonObject representation) {
    return client.post(collectionRoot, representation);
  }

  public CompletableFuture<Result<Response>> put(JsonObject representation) {
    return client.put(collectionRoot, representation);
  }

  public CompletableFuture<Result<Response>> put(String id,
    JsonObject representation) {

    return client.put(individualRecordUrl(id), representation);
  }

  public CompletableFuture<Result<Response>> delete(String id) {
    return client.delete(individualRecordUrl(id));
  }

  public CompletableFuture<Result<Response>> delete() {
    return client.delete(collectionRoot.toString());
  }

  public CompletableFuture<Result<Response>> deleteMany(CqlQuery cqlQuery) {
      return client.delete(collectionRoot, cqlQuery);
  }

  public CompletableFuture<Result<Response>> get() {
    return client.get(collectionRoot.toString());
  }

  public CompletableFuture<Result<Response>> get(String id) {
    return client.get(individualRecordUrl(id));
  }

  /**
   * Make a get request for multiple records using raw query string parameters
   * Should only be used when passing on entire query string from a client request
   * Is deprecated, and will be removed when all use replaced by explicit parsing
   * of request parameters
   *
   * @param rawQueryString raw query string to append to the URL
   * @return response from the server
   */
  public CompletableFuture<Result<Response>> getManyWithRawQueryStringParameters(
    String rawQueryString) {

    String url = isNotBlank(rawQueryString)
      ? String.format("%s?%s", collectionRoot, rawQueryString)
      : collectionRoot.toString();

    return client.get(url);
  }

  public CompletableFuture<Result<Response>> getMany(CqlQuery cqlQuery,
    PageLimit pageLimit) {

    return getMany(cqlQuery, pageLimit, noOffset());
  }

  public CompletableFuture<Result<Response>> getMany(CqlQuery cqlQuery,
    PageLimit pageLimit, Offset offset) {

    return client.get(collectionRoot, cqlQuery, pageLimit, offset);
  }

  private String individualRecordUrl(String id) {
    return String.format("%s/%s", collectionRoot, id);
  }
}
