package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;

public interface HttpResult<T> {
  boolean failed();
  boolean succeeded();

  T value();
  HttpFailure cause();

  static <T> HttpResult<T> success(T value) {
    return new SuccessfulHttpResult<>(value);
  }

  static <T> HttpResult<T> failure(HttpFailure cause) {
    return new FailedHttpResult<>(cause);
  }

  void writeNoContentSuccess(HttpServerResponse response);
}
