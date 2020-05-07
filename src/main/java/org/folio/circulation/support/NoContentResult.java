package org.folio.circulation.support;

import org.folio.circulation.support.http.server.NoContentResponse;

import io.vertx.core.http.HttpServerResponse;

public class NoContentResult implements ResponseWritableResult<Void> {
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
    NoContentResponse.noContent().writeTo(response);
  }

  public static <T> ResponseWritableResult<Void> from(Result<T> result) {
    if(result.failed()) {
      return Result.failed(result.cause());
    }
    else {
      return new NoContentResult();
    }
  }
}
