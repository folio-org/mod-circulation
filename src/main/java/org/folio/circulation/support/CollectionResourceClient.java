package org.folio.circulation.support;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
    return client.toWebClient().get(collectionRoot.toString())
      //Mimic mapping failures to fake 500 response
      //see responseConversationHandler
      .thenApply(r -> r.orElse(new Response(500, "Something went wrong",
        TEXT_PLAIN.toString())));
  }

  public CompletableFuture<Result<Response>> get(String id) {
    return client.toWebClient().get(individualRecordUrl(id));
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

      String url = getPagedCollectionUrl(encodedQuery, null, 0);

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
  public CompletableFuture<Result<Response>> getManyWithRawQueryStringParameters(
    String rawQueryString) {

    String url = isProvided(rawQueryString)
      ? String.format("%s?%s", collectionRoot, rawQueryString)
      : collectionRoot.toString();

    return client.toWebClient().get(url);
  }

  public CompletableFuture<Result<Response>> getMany(
    CqlQuery cqlQuery, Integer pageLimit) {

    return getMany(cqlQuery, pageLimit, 0);
  }

  public CompletableFuture<Result<Response>> getMany(
    CqlQuery cqlQuery, Integer pageLimit, Integer pageOffset) {

    return cqlQuery.encode()
      .map(encodedQuery -> getPagedCollectionUrl(encodedQuery, pageLimit, pageOffset))
      .after(url -> client.toWebClient().get(url));
  }

  private String getPagedCollectionUrl(String encodedQuery, Integer pageLimit, Integer pageOffset) {
    return collectionRoot + createQueryString(encodedQuery, pageLimit, pageOffset);
  }

  private static boolean isProvided(String query) {
    return isNotBlank(query);
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

    String queryParameter = prefixOnCondition(
      "query=", urlEncodedCqlQuery, CollectionResourceClient::isProvided);

    String limitParameter = prefixOnCondition(
      "limit=", pageLimit, Objects::nonNull);

    String offsetParameter = prefixOnCondition(
      "offset=", pageOffset, Objects::nonNull);

    final String queryStringParameters
      = Stream.of(queryParameter, limitParameter, offsetParameter)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining("&"));

    return prefixOnCondition(
      "?", queryStringParameters, StringUtils::isNotBlank);
  }

  private static <T> String prefixOnCondition(
    String prefix, T value, Predicate<T> condition) {

    return condition.test(value)
      ? prefix + value
      : "";
  }

  //TODO: Replace with Consumer<Result<Response>>
  private Handler<HttpClientResponse> responseConversationHandler(
    String fromUrl, Consumer<Response> responseHandler) {

    return response -> response
      .bodyHandler(buffer -> responseHandler.accept(Response.from(response, buffer, fromUrl)))
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
