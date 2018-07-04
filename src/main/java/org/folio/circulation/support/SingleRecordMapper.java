package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;

import java.util.function.Function;

public class SingleRecordMapper<T> {
  private final Function<JsonObject, T> mapper;
  private final Function<Response, HttpResult<T>> resultOnFailure;

  public SingleRecordMapper(Function<JsonObject, T> mapper) {
    this(mapper, (response -> HttpResult.failed(new ForwardOnFailure(response))));
  }

  private SingleRecordMapper(
    Function<JsonObject, T> mapper,
    Function<Response, HttpResult<T>> responseHttpResultFunction) {
    this.mapper = mapper;
    resultOnFailure = responseHttpResultFunction;
  }

  public HttpResult<T> mapFrom(Response response) {
    return response != null && response.getStatusCode() == 200
    ? HttpResult.succeeded(mapper.apply(response.getJson()))
    : resultOnFailure.apply(response);
  }
}
