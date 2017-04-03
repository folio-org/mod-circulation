package org.folio.circulation.support;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;

import java.net.URL;
import java.util.function.Consumer;

public class CollectionResourceClient {

  private final OkapiHttpClient client;
  private final URL collectionRoot;
  private final String tenantId;

  public CollectionResourceClient(OkapiHttpClient client,
                                  URL collectionRoot,
                                  String tenantId) {

    this.client = client;
    this.collectionRoot = collectionRoot;
    this.tenantId = tenantId;
  }

  public void post(Object resourceRepresentation,
                   Consumer<Response> responseHandler) {

    client.post(collectionRoot,
      resourceRepresentation,
      tenantId, responseConversationHandler(responseHandler));
  }

  public void put(String id, Object resourceRepresentation,
                     Consumer<Response> responseHandler) {

    client.put(String.format(collectionRoot + "/%s", id),
      resourceRepresentation,
      tenantId, responseConversationHandler(responseHandler));
  }

  public void get(String id, Consumer<Response> responseHandler) {
    client.get(String.format(collectionRoot + "/%s", id),
      tenantId, responseConversationHandler(responseHandler));
  }

  public void delete(String id, Consumer<Response> responseHandler) {
    client.delete(String.format(collectionRoot + "/%s", id),
      tenantId, responseConversationHandler(responseHandler));
  }

  public void delete(Consumer<Response> responseHandler) {
    client.delete(collectionRoot,
      tenantId, responseConversationHandler(responseHandler));
  }

  public void getMany(String query, Consumer<Response> responseHandler) {

    String url = isProvided(query)
      ? String.format(collectionRoot + "?%s", query)
      : collectionRoot.toString();

    client.get(url,
      tenantId, responseConversationHandler(responseHandler));
  }

  private boolean isProvided(String query) {
    return query != null && query.trim() != "";
  }

  private Handler<HttpClientResponse> responseConversationHandler(
    Consumer<Response> responseHandler) {

    return response ->
      response.bodyHandler(buffer ->
        responseHandler.accept(Response.from(response, buffer)));
  }
}
