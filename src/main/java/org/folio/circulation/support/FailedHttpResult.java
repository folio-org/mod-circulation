package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class FailedHttpResult<T> implements WritableHttpResult<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
  public void writeTo(HttpServerResponse response) {
    log.info("Writing failure response");
    cause.writeTo(response);
  }
}
