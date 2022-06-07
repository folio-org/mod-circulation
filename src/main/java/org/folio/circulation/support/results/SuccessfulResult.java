package org.folio.circulation.support.results;

import org.folio.circulation.support.failures.HttpFailure;

public class SuccessfulResult<T> implements Result<T> {
  private final T value;

  SuccessfulResult(T value) {
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
