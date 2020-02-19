package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;

public interface HttpFailure {
  void writeTo(HttpServerResponse response);
}
