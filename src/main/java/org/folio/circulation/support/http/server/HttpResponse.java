package org.folio.circulation.support.http.server;

import io.vertx.core.http.HttpServerResponse;

public interface HttpResponse {
    void writeTo(HttpServerResponse response);
}
