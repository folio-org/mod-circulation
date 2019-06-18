package org.folio.circulation.support;

import static org.folio.circulation.support.Result.of;

import java.util.function.Function;

import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class ResponseMapping {
  private ResponseMapping() { }

  public static <T> Function<Response, Result<T>> usingJson(
    Function<JsonObject, T> mapper) {

    return response -> of(() -> mapper.apply(response.getJson()));
  }
}
