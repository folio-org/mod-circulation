package org.folio.circulation.support.http;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;

import java.util.function.Function;

import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class ResponseMapping {
  private ResponseMapping() { }

  public static <T> Function<Response, Result<T>> mapUsingJson(
    Function<JsonObject, T> mapper) {

    return response -> of(() -> mapper.apply(response.getJson()));
  }

  public static <T> Function<Response, Result<T>> flatMapUsingJson(
    Function<JsonObject, Result<T>> mapper) {

    return response -> mapper.apply(response.getJson());
  }

  public static <T> Function<Response, Result<T>> forwardOnFailure() {
    return response -> failed(new ForwardOnFailure(response));
  }
}
