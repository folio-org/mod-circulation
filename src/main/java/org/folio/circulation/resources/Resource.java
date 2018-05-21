package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;

public abstract class Resource {
  protected final HttpClient client;

  public Resource(HttpClient client) {
    this.client = client;
  }

  public abstract void register(Router router);
}
