package org.folio.circulation.support.http.client;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

public class ResponseHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private ResponseHandler() { }

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
        String.format("Expected Content Type: %s Actual: %s (Body: %s)",
          expectedContentType, failingResponse.getContentType(),
          failingResponse.getBody())));
  }

  private static Handler<HttpClientResponse> responseHandler(
    CompletableFuture<Response> completed,
    Predicate<Response> expectation,
    Function<Response, Throwable> expectationFailed) {

    return vertxResponse -> vertxResponse.bodyHandler(buffer -> {
      try {
        Response response = Response.from(vertxResponse, buffer);

        log.debug("Received Response: {}: {}", response.getStatusCode(), response.getContentType());
        log.debug("Received Response Body: {}", response.getBody());

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
  }

}
