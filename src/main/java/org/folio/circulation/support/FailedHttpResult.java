package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;

public class FailedHttpResult<T> implements WritableHttpResult<T> {
  private final HttpFailure cause;

  FailedHttpResult(HttpFailure cause) {
    this.cause = cause;
  }

  @Override
  public T value() {
    return null;
  }

  @Override
  public HttpFailure cause() {
    return cause;
  }

  @Override
  public boolean failed() {
    return true;
  }

  @Override
  public boolean succeeded() {
    return false;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    cause.writeTo(response);
  }
}
