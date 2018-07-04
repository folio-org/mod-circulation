package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;

import java.util.function.Function;

public class SingleRecordMapper<T> {
  private final Function<JsonObject, T> mapper;

  public SingleRecordMapper(Function<JsonObject, T> mapper) {
    this.mapper = mapper;
  }

  public HttpResult<T> mapFrom(Response response) {
    return response != null && response.getStatusCode() == 200
    ? HttpResult.succeeded(mapper.apply(response.getJson()))
    : HttpResult.failed(new ForwardOnFailure(response));
  }
}
