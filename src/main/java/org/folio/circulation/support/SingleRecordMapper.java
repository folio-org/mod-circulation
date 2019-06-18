package org.folio.circulation.support;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.of;

import java.util.function.Function;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;

import io.vertx.core.json.JsonObject;

public class SingleRecordMapper<T> {
  private final ResponseInterpreter<T> interpreter;

  SingleRecordMapper(Function<JsonObject, T> mapper) {
    this(mapper, (SingleRecordMapper::mapResponseToFailure));
  }

  public SingleRecordMapper(
    Function<JsonObject, T> mapper,
    Function<Response, Result<T>> onFailure) {

    this(new ResponseInterpreter<T>()
      .flatMapOn(200, r -> of(() -> mapper.apply(r.getJson())))
      .otherwise(onFailure));
  }

  SingleRecordMapper(ResponseInterpreter<T> interpreter) {
    this.interpreter = interpreter;
  }

  Result<T> mapFrom(Response response) {
    return interpreter.apply(response);
  }

  private static <T> ResponseWritableResult<T> mapResponseToFailure(Response response) {
    if(isNull(response)) {
      return failed(new ServerErrorFailure("Null response received"));
    }

    //TODO: Include the request URL here
    final String diagnosticError = String.format(
      "HTTP request to \"%s\" failed, status code: %s, response: \"%s\"",
      response.getFromUrl(), response.getStatusCode(), response.getBody());

    return failed(new ServerErrorFailure(diagnosticError));
  }
}
