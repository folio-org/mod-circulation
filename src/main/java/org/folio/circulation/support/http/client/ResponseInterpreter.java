package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.Result.failed;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;

public class ResponseInterpreter<T> {
  private final Map<Integer, Function<Response, Result<T>>> responseMappers;
  private final Function<Response, Result<T>> unexpectedResponseMapper;

  private ResponseInterpreter(Map<Integer, Function<Response, Result<T>>> responseMappers,
                              Function<Response, Result<T>> unexpectedResponseMapper) {

    this.unexpectedResponseMapper = unexpectedResponseMapper;
    this.responseMappers = responseMappers;
  }

  ResponseInterpreter() {
    this(new HashMap<>(), ResponseInterpreter::defaultUnexpectedResponseMapper);
  }

  ResponseInterpreter<T> flatMapOn(Integer status, Function<Response, Result<T>> mapper) {
    final HashMap<Integer, Function<Response, Result<T>>> newMappers = new HashMap<>(responseMappers);

    newMappers.put(status, mapper);

    return new ResponseInterpreter<>(newMappers, unexpectedResponseMapper);
  }

  ResponseInterpreter<T> otherwise(Function<Response, Result<T>> unexpectedResponseMapper) {
    return new ResponseInterpreter<>(responseMappers, unexpectedResponseMapper);
  }

  public Result<T> apply(Response response) {
    try {
      final Integer statusCode = response.getStatusCode();

      return responseMappers.getOrDefault(statusCode, unexpectedResponseMapper)
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
