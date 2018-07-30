package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.SuccessResponse;

public class NoContentHttpResult implements WritableHttpResult<Void> {
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

  public static <T>  WritableHttpResult<Void> from(HttpResult<T> result) {
    if(result.failed()) {
      return HttpResult.failed(result.cause());
    }
    else {
      return new NoContentHttpResult();
    }
  }

  public static WritableHttpResult<Void> from(Response response) {
    return from(response, 204);
  }

  public static WritableHttpResult<Void> from(
    Response response,
    Integer expectedStatusCode) {

    if(response.getStatusCode() == expectedStatusCode) {
      return new NoContentHttpResult();
    }
    else {
      return HttpResult.failed(new ForwardOnFailure(response));
    }
  }
}
