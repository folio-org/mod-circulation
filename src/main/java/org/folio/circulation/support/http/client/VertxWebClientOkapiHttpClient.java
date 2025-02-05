package org.folio.circulation.support.http.client;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.REQUEST_ID;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.http.OkapiHeader.TOKEN;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;
import static org.folio.circulation.support.http.client.Response.responseFrom;

import java.net.URL;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ServerErrorFailure;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.core.http.HttpMethod;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class VertxWebClientOkapiHttpClient implements OkapiHttpClient {
  private static final Duration DEFAULT_TIMEOUT = Duration.of(20, SECONDS);
  private static final String ACCEPT = HttpHeaderNames.ACCEPT.toString();

  private final WebClient webClient;
  private final URL okapiUrl;
  private final String tenantId;
  private final String token;
  private final String userId;
  private final String requestId;

  public static OkapiHttpClient createClientUsing(HttpClient httpClient,
    URL okapiUrl, String tenantId, String token, String userId, String requestId) {

    return new VertxWebClientOkapiHttpClient(WebClient.wrap(httpClient),
      okapiUrl, tenantId, token, userId, requestId);
  }

  private VertxWebClientOkapiHttpClient(WebClient webClient, URL okapiUrl,
    String tenantId, String token, String userId, String requestId) {

    this.webClient = webClient;
    this.okapiUrl = okapiUrl;
    this.tenantId = tenantId;
    this.token = token;
    this.userId = userId;
    this.requestId = requestId;
  }

  @Override
  public CompletableFuture<Result<Response>> post(URL url, JsonObject body) {
    return post(url.toString(), body, DEFAULT_TIMEOUT);
  }

  @Override
  public CompletableFuture<Result<Response>> post(String url, JsonObject body) {
    return post(url, body, DEFAULT_TIMEOUT);
  }

  @Override
  public CompletableFuture<Result<Response>> post(String url,
    JsonObject body, Duration timeout) {

    final CompletableFuture<AsyncResult<HttpResponse<Buffer>>> futureResponse
      = new CompletableFuture<>();

    final HttpRequest<Buffer> request = withStandardHeaders(
      webClient.requestAbs(HttpMethod.POST, url));

    request
      .timeout(timeout.toMillis())
      .sendJsonObject(body, futureResponse::complete);

    return futureResponse
      .thenApply(asyncResult -> mapAsyncResultToResult(url, asyncResult));
  }

  @Override
  public CompletableFuture<Result<Response>> get(String url,
    Duration timeout, QueryParameter... queryParameters) {

    final CompletableFuture<AsyncResult<HttpResponse<Buffer>>> futureResponse
      = new CompletableFuture<>();

    final HttpRequest<Buffer> request = withStandardHeaders(
      webClient.requestAbs(HttpMethod.GET, url));

    Stream.of(queryParameters)
      .forEach(parameter -> parameter.consume(request::addQueryParam));

    request
      .timeout(timeout.toMillis())
      .send(futureResponse::complete);

    return futureResponse
      .thenApply(asyncResult -> mapAsyncResultToResult(url, asyncResult));
  }

  @Override
  public CompletableFuture<Result<Response>> get(URL url,
    QueryParameter... queryParameters) {

    return get(url.toString(), queryParameters);
  }

  @Override
  public CompletableFuture<Result<Response>> get(String url,
    QueryParameter... queryParameters) {

    return get(url, DEFAULT_TIMEOUT, queryParameters);
  }

  @Override
  public CompletableFuture<Result<Response>> put(URL url, JsonObject body) {
    return put(url.toString(), body, DEFAULT_TIMEOUT);
  }

  @Override
  public CompletableFuture<Result<Response>> put(String url, JsonObject body) {
    return put(url, body, DEFAULT_TIMEOUT);
  }

  @Override
  public CompletableFuture<Result<Response>> put(String url, JsonObject body,
    Duration timeout) {

    final CompletableFuture<AsyncResult<HttpResponse<Buffer>>> futureResponse
      = new CompletableFuture<>();

    final HttpRequest<Buffer> request = withStandardHeaders(
      webClient.requestAbs(HttpMethod.PUT, url));

    request
      .timeout(timeout.toMillis())
      .sendJsonObject(body, futureResponse::complete);

    return futureResponse
      .thenApply(asyncResult -> mapAsyncResultToResult(url, asyncResult));
  }

  @Override
  public CompletableFuture<Result<Response>> delete(URL url,
    QueryParameter... queryParameters) {

    return delete(url.toString(), queryParameters);
  }

  @Override
  public CompletableFuture<Result<Response>> delete(String url,
    QueryParameter... queryParameters) {

    return delete(url, DEFAULT_TIMEOUT, queryParameters);
  }

  @Override
  public CompletableFuture<Result<Response>> delete(String url,
    Duration timeout, QueryParameter... queryParameters) {

    final CompletableFuture<AsyncResult<HttpResponse<Buffer>>> futureResponse
      = new CompletableFuture<>();

    final HttpRequest<Buffer> request = withStandardHeaders(
      webClient.requestAbs(HttpMethod.DELETE, url));

    Stream.of(queryParameters)
      .forEach(parameter -> parameter.consume(request::addQueryParam));

    request
      .timeout(timeout.toMillis())
      .send(futureResponse::complete);

    return futureResponse
      .thenApply(asyncResult -> mapAsyncResultToResult(url, asyncResult));
  }

  private HttpRequest<Buffer> withStandardHeaders(HttpRequest<Buffer> request) {
    log.debug("withStandardHeaders:: url={}, tenantId={}", request.uri(), tenantId);
    return request
      .putHeader(ACCEPT, "application/json, text/plain")
      .putHeader(OKAPI_URL, okapiUrl.toString())
      .putHeader(TENANT, this.tenantId)
      .putHeader(TOKEN, this.token)
      .putHeader(USER_ID, this.userId)
      .putHeader(REQUEST_ID, this.requestId);
  }

  private static Result<Response> mapAsyncResultToResult(String url,
    AsyncResult<HttpResponse<Buffer>> asyncResult) {

    return asyncResult.succeeded()
      ? succeeded(responseFrom(url, asyncResult.result()))
      : failed(new ServerErrorFailure(asyncResult.cause()));
  }
}
