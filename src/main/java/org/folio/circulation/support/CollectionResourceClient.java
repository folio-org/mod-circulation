package org.folio.circulation.support;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.folio.circulation.support.http.client.Offset.noOffset;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

public class CollectionResourceClient {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final OkapiHttpClient client;
  private final URL collectionRoot;

  public CollectionResourceClient(OkapiHttpClient client, URL collectionRoot) {
    this.client = client;
    this.collectionRoot = collectionRoot;
  }

  public CompletableFuture<Result<Response>> post(JsonObject representation) {
    return client.toWebClient().post(collectionRoot, representation);
  }

  public CompletableFuture<Result<Response>> put(JsonObject representation) {
    return client.toWebClient().put(collectionRoot, representation);
  }

  public CompletableFuture<Result<Response>> put(String id,
    JsonObject representation) {

    return client.toWebClient().put(individualRecordUrl(id), representation);
  }

  public CompletableFuture<Result<Response>> delete(String id) {
    return internalDelete(individualRecordUrl(id));
  }

  public CompletableFuture<Result<Response>> delete() {
    return internalDelete(collectionRoot.toString());
  }

  public CompletableFuture<Result<Response>> deleteMany(CqlQuery cqlQuery) {
      return client.toWebClient().delete(collectionRoot, cqlQuery);
  }

  private CompletableFuture<Result<Response>> internalDelete(String url) {
    return client.toWebClient().delete(url);
  }

  public CompletableFuture<Result<Response>> get() {
    return client.toWebClient().get(collectionRoot.toString());
  }

  public CompletableFuture<Result<Response>> get(String id) {
    return client.toWebClient().get(individualRecordUrl(id));
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

    String url = isProvided(rawQueryString)
      ? String.format("%s?%s", collectionRoot, rawQueryString)
      : collectionRoot.toString();

    return client.toWebClient().get(url);
  }

  public CompletableFuture<Result<Response>> getMany(CqlQuery cqlQuery,
    PageLimit pageLimit) {

    return getMany(cqlQuery, pageLimit, noOffset());
  }

  public CompletableFuture<Result<Response>> getMany(CqlQuery cqlQuery,
                                                     PageLimit pageLimit, Offset offset) {

    return client.toWebClient().get(collectionRoot, cqlQuery, pageLimit, offset);
  }

  private static boolean isProvided(String query) {
    return isNotBlank(query);
  }

  //TODO: Replace with Consumer<Result<Response>>
  private Handler<HttpClientResponse> responseConversationHandler(
    String fromUrl, Consumer<Response> responseHandler) {

    return response -> response
      .bodyHandler(buffer -> responseHandler.accept(
        Response.from(response, buffer, fromUrl)))
      .exceptionHandler(ex -> {
        log.error("Unhandled exception in body handler", ex);
        String trace = ExceptionUtils.getStackTrace(ex);
        responseHandler.accept(new Response(500, trace, TEXT_PLAIN.toString()));
      });
  }

  private Handler<HttpClientResponse> responseConversationHandler(
    Consumer<Response> responseHandler) {

    return responseConversationHandler(null, responseHandler);
  }

  private String individualRecordUrl(String id) {
    return String.format("%s/%s", collectionRoot, id);
  }
}
