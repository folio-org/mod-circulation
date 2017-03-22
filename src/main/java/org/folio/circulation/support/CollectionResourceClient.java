package org.folio.circulation.support;

import org.folio.circulation.support.http.client.HttpClient;
import org.folio.circulation.support.http.client.Response;

import java.net.URL;
import java.util.function.Consumer;

public class CollectionResourceClient {

  private final HttpClient client;
  private final URL collectionRoot;
  private final String tenantId;

  public CollectionResourceClient(HttpClient client,
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
      tenantId, response ->
        response.bodyHandler(buffer ->
          responseHandler.accept(Response.from(response, buffer))));
  }

  public void put(String id, Object resourceRepresentation,
                     Consumer<Response> responseHandler) {

    client.put(String.format(collectionRoot + "/%s", id),
      resourceRepresentation,
      tenantId, response ->
        response.bodyHandler(buffer ->
          responseHandler.accept(Response.from(response, buffer))));
  }

  public void get(String id, Consumer<Response> responseHandler) {
    client.get(String.format(collectionRoot + "/%s", id),
      tenantId, response ->
        response.bodyHandler(buffer ->
          responseHandler.accept(Response.from(response, buffer))));
  }

  public void delete(String id, Consumer<Response> responseHandler) {
    client.delete(String.format(collectionRoot + "/%s", id),
      tenantId, response ->
        response.bodyHandler(buffer ->
          responseHandler.accept(Response.from(response, buffer))));
  }

  public void delete(Consumer<Response> responseHandler) {
    client.delete(collectionRoot,
      tenantId, response ->
        response.bodyHandler(buffer ->
          responseHandler.accept(Response.from(response, buffer))));
  }

  public void getMany(String query, Consumer<Response> responseHandler) {

    String url = isProvided(query)
      ? String.format(collectionRoot + "?%s", query)
      : collectionRoot.toString();

    client.get(url,
      tenantId, response ->
        response.bodyHandler(buffer ->
          responseHandler.accept(Response.from(response, buffer))));
  }

  private boolean isProvided(String query) {
    return query != null && query.trim() != "";
  }
}
