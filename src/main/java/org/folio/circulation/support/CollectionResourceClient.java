package org.folio.circulation.support;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
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

  public void post(Object resourceRepresentation,
                   Consumer<Response> responseHandler) {

    client.post(collectionRoot,
      resourceRepresentation,
      responseConversationHandler(responseHandler));
  }

  public void put(Object resourceRepresentation,
      Consumer<Response> responseHandler) {

    client.put(collectionRoot,
        resourceRepresentation,
        responseConversationHandler(responseHandler));
  }

  public void put(String id, Object resourceRepresentation,
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

  public void getMany(String query, Consumer<Response> responseHandler) {
      String url = isProvided(query)
        ? String.format("%s?%s", collectionRoot, query)
        : collectionRoot.toString();

      client.get(url, responseConversationHandler(responseHandler));
  }

  public void getMany(
    String cqlQuery,
    Integer pageLimit,
    Integer pageOffset,
    Consumer<Response> responseHandler) {

    //TODO: Replace with query string creator that checks each parameter
    String url = isProvided(cqlQuery)
      ? String.format("%s?query=%s&limit=%s&offset=%s", collectionRoot, cqlQuery,
      pageLimit, pageOffset)
      : collectionRoot.toString();

    client.get(url, responseConversationHandler(responseHandler));
  }

  private boolean isProvided(String query) {
    return StringUtils.isNotBlank(query);
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
