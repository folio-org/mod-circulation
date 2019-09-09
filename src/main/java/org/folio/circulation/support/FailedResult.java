package org.folio.circulation.support;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpServerResponse;

public class FailedResult<T> implements ResponseWritableResult<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
  public void writeTo(HttpServerResponse response) {
    log.info("Writing failure response");
    cause.writeTo(response);
  }

  @Override
  public String toString() {
    return String.format("failed result due to a %s", cause.toString());
  }
}
