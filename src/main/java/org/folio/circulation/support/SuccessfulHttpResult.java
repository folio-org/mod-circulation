package org.folio.circulation.support;

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
  public boolean failed() {
    return false;
  }

}
