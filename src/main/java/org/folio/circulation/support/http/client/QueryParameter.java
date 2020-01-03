package org.folio.circulation.support.http.client;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;

public interface QueryParameter {
  void writeTo(HttpRequest<Buffer> request);
}
