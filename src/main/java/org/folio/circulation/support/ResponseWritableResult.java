package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;

public interface ResponseWritableResult<T> extends Result<T> {
  void writeTo(HttpServerResponse response);
}
