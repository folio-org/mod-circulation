package org.folio.circulation.support;

public class FailedHttpResult<T> implements HttpResult<T> {
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
}
