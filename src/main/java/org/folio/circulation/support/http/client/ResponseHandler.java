package org.folio.circulation.support.http.client;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

public class ResponseHandler {
  public static Handler<HttpClientResponse> any(
    CompletableFuture<Response> completed) {

    return responseHandler(completed,
      responseToCheck -> true,
      failingResponse -> null);
  }

  public static Handler<HttpClientResponse> json(
    CompletableFuture<Response> completed) {

    return strictContentType(completed, "application/json");
  }

  public static Handler<HttpClientResponse> text(
    CompletableFuture<Response> completed) {

    return strictContentType(completed, "text/plain");
  }

  private static Handler<HttpClientResponse> strictContentType(
    CompletableFuture<Response> completed,
    String expectedContentType) {

    return responseHandler(completed,
      responseToCheck ->
        responseToCheck.getContentType().contains(expectedContentType),
      failingResponse -> new Exception(
        String.format("Expected Content Type: %s Actual: %s", "",
          failingResponse.getContentType(), expectedContentType)));
  }

  private static Handler<HttpClientResponse> responseHandler(
    CompletableFuture<Response> completed,
    Predicate<Response> expectation,
    Function<Response, Throwable> expectationFailed) {

    return vertxResponse -> {
        vertxResponse.bodyHandler(buffer -> {
          try {

            Response response = Response.from(vertxResponse, buffer);

            if(expectation.test(response)) {
              completed.complete(response);
            }
            else {
              completed.completeExceptionally(
                expectationFailed.apply(response));
            }
          } catch (Exception e) {
            completed.completeExceptionally(e);
          }
        });
    };
  }

}
