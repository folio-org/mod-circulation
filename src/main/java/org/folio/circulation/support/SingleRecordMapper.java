package org.folio.circulation.support;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class SingleRecordMapper<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Function<JsonObject, T> mapper;
  private final Function<Response, Result<T>> resultOnFailure;

  static <T> SingleRecordMapper<T> notFound(
    Function<JsonObject, T> mapper, Result<T> notFoundResult) {

    return new SingleRecordMapper<>(mapper, notFoundMapper(notFoundResult));
  }

  SingleRecordMapper(Function<JsonObject, T> mapper) {
    this(mapper, (SingleRecordMapper::mapResponseToFailure));
  }

  public SingleRecordMapper(
    Function<JsonObject, T> mapper,
    Function<Response, Result<T>> onFailure) {
    this.mapper = mapper;
    resultOnFailure = onFailure;
  }

  Result<T> mapFrom(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        return succeeded(mapper.apply(response.getJson()));
      } else {
        return resultOnFailure.apply(response);
      }
    }
    else {
      log.warn("Did not receive response to request");
      return failed(new ServerErrorFailure("Did not receive response to request"));
    }
  }

  private static <T> Function<Response, Result<T>> notFoundMapper(Result<T> result) {
    return response -> response.getStatusCode() == 404
      ? result
      : mapResponseToFailure(response);
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
