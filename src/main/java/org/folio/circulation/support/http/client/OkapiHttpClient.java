package org.folio.circulation.support.http.client;

import java.net.URL;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public interface OkapiHttpClient {
  CompletableFuture<Result<Response>> post(URL url, JsonObject body);

  CompletableFuture<Result<Response>> post(String url, JsonObject body);

  CompletableFuture<Result<Response>> post(String url,
      JsonObject body, Duration timeout);

  CompletableFuture<Result<Response>> get(String url,
      Duration timeout, QueryParameter... queryParameters);

  CompletableFuture<Result<Response>> get(URL url,
      QueryParameter... queryParameters);

  CompletableFuture<Result<Response>> get(String url,
      QueryParameter... queryParameters);

  CompletableFuture<Result<Response>> put(URL url, JsonObject body);

  CompletableFuture<Result<Response>> put(String url, JsonObject body);

  CompletableFuture<Result<Response>> put(String url, JsonObject body,
      Duration timeout);

  CompletableFuture<Result<Response>> delete(URL url,
      QueryParameter... queryParameters);

  CompletableFuture<Result<Response>> delete(String url,
      QueryParameter... queryParameters);

  CompletableFuture<Result<Response>> delete(String url,
      Duration timeout, QueryParameter... queryParameters);
}
