package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.Result.failed;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;

class ResponseInterpreter<T> {
  private final Map<Integer, Function<Response, Result<T>>> maps = new HashMap<>();
  private final Function<Response, Result<T>> onUnexpectedResponse;

  private ResponseInterpreter(Function<Response, Result<T>> onUnexpectedResponse) {
    this.onUnexpectedResponse = onUnexpectedResponse;
  }

  ResponseInterpreter() {
    this(ResponseInterpreter::defaultUnexpectedResponseMapper);
  }

  ResponseInterpreter<T> flatMapOn(Integer status, Function<Response, Result<T>> mapper) {
    maps.put(status, mapper);
    return this;
  }

  public Result<T> apply(Response response) {
    try {
      final Integer statusCode = response.getStatusCode();

      return maps.getOrDefault(statusCode, onUnexpectedResponse)
        .apply(response);
    }
    catch (Exception e) {
      return failed(e);
    }
  }

  private static <R> Result<R> defaultUnexpectedResponseMapper(Response response) {
    final String diagnosticError = String.format(
      "HTTP request to \"%s\" failed, status code: %s, response: \"%s\"",
      response.getFromUrl(), response.getStatusCode(), response.getBody());

    return failed(new ServerErrorFailure(diagnosticError));
  }
}
