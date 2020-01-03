package org.folio.circulation.support.http.client;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;

public class NamedQueryParameter implements QueryParameter {
  private final String name;
  private final String value;

  public static NamedQueryParameter namedParameter(String name, String value) {
    return new NamedQueryParameter(name, value);
  }

  private NamedQueryParameter(String name, String value) {
    this.name = name;
    this.value = value;
  }

  @Override
  public void writeTo(HttpRequest<Buffer> request) {
    request.addQueryParam(name, value);
  }
}
