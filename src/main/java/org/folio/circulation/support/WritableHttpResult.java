package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;

public interface WritableHttpResult<T> extends HttpResult<T> {
  void writeTo(HttpServerResponse response);
}
