package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;

abstract class CollectionResource {
  protected final HttpClient client;

  CollectionResource(HttpClient client) {
    this.client = client;
  }
}
