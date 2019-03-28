package org.folio.circulation.support;

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

  public static <T> SingleRecordMapper<T> notFound(Function<JsonObject, T> mapper, Result<T> notFoundResult) {
    return new SingleRecordMapper<>(mapper, notFoundMapper(notFoundResult));
  }

  SingleRecordMapper(Function<JsonObject, T> mapper) {
    this(mapper, (response -> failed(new ForwardOnFailure(response))));
  }

  public SingleRecordMapper(
    Function<JsonObject, T> mapper,
    Function<Response, Result<T>> responseHttpResultFunction) {
    this.mapper = mapper;
    resultOnFailure = responseHttpResultFunction;
  }

  Result<T> mapFrom(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        return succeeded(mapper.apply(response.getJson()));
      } else {
        return this.resultOnFailure.apply(response);
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
      : failed(new ForwardOnFailure(response));
  }
}
