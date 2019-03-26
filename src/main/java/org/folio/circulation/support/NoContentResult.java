package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.SuccessResponse;

public class NoContentResult implements ResponseWritableResult<Void> {
  @Override
  public boolean failed() {
    return false;
  }

  @Override
  public Void value() {
    return null;
  }

  @Override
  public HttpFailure cause() {
    return null;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    SuccessResponse.noContent(response);
  }

  public static <T> ResponseWritableResult<Void> from(Result<T> result) {
    if(result.failed()) {
      return Result.failed(result.cause());
    }
    else {
      return new NoContentResult();
    }
  }

  public static ResponseWritableResult<Void> from(Response response) {
    return from(response, 204);
  }

  public static ResponseWritableResult<Void> from(
    Response response,
    Integer expectedStatusCode) {

    if(response.getStatusCode() == expectedStatusCode) {
      return new NoContentResult();
    }
    else {
      return Result.failed(new ForwardOnFailure(response));
    }
  }
}
