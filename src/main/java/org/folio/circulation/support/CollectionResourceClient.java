package org.folio.circulation.support;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.entity.ContentType;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.function.Consumer;

public class CollectionResourceClient {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final OkapiHttpClient client;
  private final URL collectionRoot;

  public CollectionResourceClient(OkapiHttpClient client,
                                  URL collectionRoot) {

    this.client = client;
    this.collectionRoot = collectionRoot;
  }

  public void post(JsonObject resourceRepresentation,
                   Consumer<Response> responseHandler) {

    client.post(collectionRoot,
      resourceRepresentation,
      responseConversationHandler(responseHandler));
  }

  public void put(JsonObject resourceRepresentation,
      Consumer<Response> responseHandler) {

    client.put(collectionRoot,
        resourceRepresentation,
        responseConversationHandler(responseHandler));
  }

  public void put(String id, JsonObject resourceRepresentation,
                     Consumer<Response> responseHandler) {

    client.put(individualRecordUrl(id),
      resourceRepresentation,
      responseConversationHandler(responseHandler));
  }

  public void get(Consumer<Response> responseHandler) {
    client.get(collectionRoot,
      responseConversationHandler(responseHandler));
  }

  public void get(String id, Consumer<Response> responseHandler) {
    client.get(individualRecordUrl(id),
      responseConversationHandler(responseHandler));
  }

  public void delete(String id, Consumer<Response> responseHandler) {
    client.delete(individualRecordUrl(id),
      responseConversationHandler(responseHandler));
  }

  public void delete(Consumer<Response> responseHandler) {
    client.delete(collectionRoot,
      responseConversationHandler(responseHandler));
  }

  public void getMany(String urlEncodedQuery, Consumer<Response> responseHandler) {
      String url = isProvided(urlEncodedQuery)
        ? String.format("%s?%s", collectionRoot, urlEncodedQuery)
        : collectionRoot.toString();

      client.get(url, responseConversationHandler(responseHandler));
  }

  public void getMany(
    String urlEncodedCqlQuery,
    Integer pageLimit,
    Integer pageOffset,
    Consumer<Response> responseHandler) {

    String url = collectionRoot + createQueryString(urlEncodedCqlQuery, pageLimit, pageOffset);
    client.get(url, responseConversationHandler(responseHandler));
  }

  /**
   * Get all records (up to Integer.MAX_VALUE).
   * @param responseHandler  the result
   */
  public void getAll(Consumer<Response> responseHandler) {
    getMany(null, Integer.MAX_VALUE, null, responseHandler);
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
  static String createQueryString(String urlEncodedCqlQuery, Integer pageLimit, Integer pageOffset) {
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

    return response ->
      response
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
