package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.folio.circulation.support.results.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResponseInterpreter<T> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Map<Integer, Function<Response, Result<T>>> responseMappers;
  private final Function<Response, Result<T>> unexpectedResponseMapper;

  private ResponseInterpreter(Map<Integer, Function<Response, Result<T>>> responseMappers,
                              Function<Response, Result<T>> unexpectedResponseMapper) {

    this.unexpectedResponseMapper = unexpectedResponseMapper;
    this.responseMappers = responseMappers;
  }

  public ResponseInterpreter() {
    this(new HashMap<>(), ResponseInterpreter::defaultUnexpectedResponseMapper);
  }

  public ResponseInterpreter<T> flatMapOn(Integer status,
                                          Function<Response, Result<T>> mapper) {

    final HashMap<Integer, Function<Response, Result<T>>> newMappers
      = new HashMap<>(responseMappers);

    newMappers.put(status, mapper);

    return new ResponseInterpreter<>(newMappers, unexpectedResponseMapper);
  }

  public ResponseInterpreter<T> on(Integer status, Result<T> result) {
    return flatMapOn(status, response -> result);
  }

  public ResponseInterpreter<T> otherwise(
    Function<Response, Result<T>> unexpectedResponseMapper) {

    return new ResponseInterpreter<>(responseMappers, unexpectedResponseMapper);
  }

  public Result<T> apply(Response response) {
    if (response == null) {
      log.warn("Cannot interpret null response");
      return failedDueToServerError("Cannot interpret null response");
    }

    try {
      log.debug("Response received: {}", response);

      final Integer statusCode = response.getStatusCode();

      return responseMappers.getOrDefault(statusCode, unexpectedResponseMapper)
        .apply(response);
    }
    catch (Exception e) {
      return failedDueToServerError(e);
    }
  }

  public Result<T> flatMap(Result<Response> response) {
    return response.next(this::apply);
  }

  private static <R> Result<R> defaultUnexpectedResponseMapper(Response response) {
    final String diagnosticError = String.format(
      "HTTP request to \"%s\" failed, status code: %s, response: \"%s\"",
      response.getFromUrl(), response.getStatusCode(), response.getBody());

    return failedDueToServerError(diagnosticError);
  }
}
