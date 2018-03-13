package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.server.SuccessResponse;

public class NoContentHttpResult implements WritableHttpResult<Void> {
  @Override
  public boolean failed() {
    return false;
  }

  @Override
  public Void value() {
    return null;
  }

  @Override
  public HttpFailure cause() {
    return null;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    SuccessResponse.noContent(response);
  }

  public static <T>  WritableHttpResult<Void> from(HttpResult<T> result) {
    if(result.failed()) {
      return HttpResult.failure(result.cause());
    }
    else {
      return new NoContentHttpResult();
    }
  }
}
