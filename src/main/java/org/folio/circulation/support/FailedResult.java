package org.folio.circulation.support;

public class FailedResult<T> implements Result<T> {
  private final HttpFailure cause;

  FailedResult(HttpFailure cause) {
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
  public String toString() {
    return String.format("failed result due to a %s", cause.toString());
  }
}
