package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.server.SuccessResponse;

public class SuccessfulHttpResult<T> implements HttpResult<T> {
  private final T value;

  SuccessfulHttpResult(T value) {
    this.value = value;
  }

  @Override
  public T value() {
    return value;
  }

  @Override
  public HttpFailure cause() {
    return null;
  }

  @Override
  public void writeNoContentSuccess(HttpServerResponse response) {
    SuccessResponse.noContent(response);
  }

  @Override
  public boolean failed() {
    return false;
  }

  @Override
  public boolean succeeded() {
    return true;
  }
}
