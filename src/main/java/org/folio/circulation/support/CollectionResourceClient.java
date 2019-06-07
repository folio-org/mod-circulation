package org.folio.circulation.support;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.entity.ContentType;
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

  public CollectionResourceClient(
    OkapiHttpClient client,
    URL collectionRoot) {

    this.client = client;
    this.collectionRoot = collectionRoot;
  }

  public CompletableFuture<Response> post(
    JsonObject resourceRepresentation) {

    CompletableFuture<Response> future = new CompletableFuture<>();

    client.post(collectionRoot,
      resourceRepresentation,
      responseConversationHandler(future::complete));

    return future;
  }

  public CompletableFuture<Response> put(
    JsonObject resourceRepresentation) {

    final CompletableFuture<Response> future = new CompletableFuture<>();

    client.put(collectionRoot,
      resourceRepresentation,
      responseConversationHandler(future::complete));

    return future;
  }

  public CompletableFuture<Response> put(
    String id,
    JsonObject resourceRepresentation) {

    CompletableFuture<Response> future = new CompletableFuture<>();

    client.put(individualRecordUrl(id),
      resourceRepresentation,
      responseConversationHandler(future::complete));

    return future;
  }

  public CompletableFuture<Response> get() {
    final CompletableFuture<Response> future = new CompletableFuture<>();

    client.get(collectionRoot,
      responseConversationHandler(future::complete));

    return future;
  }

  public CompletableFuture<Response> get(String id) {
    final CompletableFuture<Response> future = new CompletableFuture<>();

    client.get(individualRecordUrl(id),
      responseConversationHandler(future::complete));

    return future;
  }

  public CompletableFuture<Response> delete(String id) {
    final CompletableFuture<Response> future = new CompletableFuture<>();

    client.delete(individualRecordUrl(id),
      responseConversationHandler(future::complete));

    return future;
  }

  public CompletableFuture<Response> delete() {
    final CompletableFuture<Response> future = new CompletableFuture<>();

    client.delete(collectionRoot, responseConversationHandler(future::complete));

    return future;
  }

  public CompletableFuture<Result<Response>> deleteMany(CqlQuery cqlQuery) {
    return cqlQuery.encode().after(encodedQuery -> {
      final CompletableFuture<Response> future = new CompletableFuture<>();

      String url = collectionRoot + createQueryString(encodedQuery, null, 0);

      client.delete(url, responseConversationHandler(future::complete));

      return future.thenApply(Result::succeeded);
    });
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
  public CompletableFuture<Response> getManyWithRawQueryStringParameters(
    String rawQueryString) {

    final CompletableFuture<Response> future = new CompletableFuture<>();

    String url = isProvided(rawQueryString)
      ? String.format("%s?%s", collectionRoot, rawQueryString)
      : collectionRoot.toString();

    client.get(url, responseConversationHandler(future::complete));

    return future;
  }

  public CompletableFuture<Result<Response>> getMany(
    CqlQuery cqlQuery, Integer pageLimit) {

    if (pageLimit == null) {
      return getManyNoLimit(cqlQuery);
    }

    return cqlQuery.encode().after(encodedQuery -> {
        final CompletableFuture<Response> future = new CompletableFuture<>();

        String url = collectionRoot + createQueryString(encodedQuery, pageLimit, 0);

        client.get(url, responseConversationHandler(future::complete));

        return future.thenApply(Result::succeeded);
      });
  }

  public CompletableFuture<Result<Response>> getManyNoLimit(CqlQuery cqlQuery) {

    return cqlQuery.encode().after(encodedQuery -> {
      final CompletableFuture<Response> future = new CompletableFuture<>();

      String url = collectionRoot + createQueryString(encodedQuery, null, 0);

      client.get(url, responseConversationHandler(future::complete));

      return future.thenApply(Result::succeeded);
    });
  }

  private static boolean isProvided(String query) {
    return StringUtils.isNotBlank(query);
  }

  /**
   * Combine the optional parameters to a query string.
   * <p>
   * createQueryString("field%3Da", 5, 10) = "?query=field%3Da&limit=5&offset=10"
   * <p>
   * createQueryString(null, 5, null) = "?limit=5"
   * <p>
   * createQueryString(null, null, null) = ""
   *
   * @param urlEncodedCqlQuery  the URL encoded String for the query parameter, may be null or empty for none
   * @param pageLimit  the value for the limit parameter, may be null for none
   * @param pageOffset  the value for the offset parameter, may be null for none
   * @return the query string, may be empty
   */
  static String createQueryString(
    String urlEncodedCqlQuery,
    Integer pageLimit,
    Integer pageOffset) {

    String query = "";

    if (isProvided(urlEncodedCqlQuery)) {
      query += "?query=" + urlEncodedCqlQuery;
    }
    if (pageLimit != null) {
      query += query.isEmpty() ? "?" : "&";
      query += "limit=" + pageLimit;
    }
    if (pageOffset != null) {
      query += query.isEmpty() ? "?" : "&";
      query += "offset=" + pageOffset;
    }
    return query;
  }

  private Handler<HttpClientResponse> responseConversationHandler(
    Consumer<Response> responseHandler) {

    return response -> response
      .bodyHandler(buffer -> responseHandler.accept(Response.from(response, buffer)))
      .exceptionHandler(ex -> {
        log.error("Unhandled exception in body handler", ex);
        String trace = ExceptionUtils.getStackTrace(ex);
        responseHandler.accept(new Response(500, trace, ContentType.TEXT_PLAIN.toString()));
      });
  }

  private String individualRecordUrl(String id) {
    return String.format("%s/%s", collectionRoot, id);
  }
}
