package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.REQUEST_ID;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.http.OkapiHeader.TOKEN;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.Json;

public class OkapiHttpClient {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final HttpClient client;
  private final URL okapiUrl;
  private final String tenantId;
  private final String token;
  private final String userId;
  private final String requestId;
  private final Consumer<Throwable> exceptionHandler;

  public OkapiHttpClient(
    HttpClient httpClient,
    URL okapiUrl,
    String tenantId,
    String token,
    String userId,
    String requestId,
    Consumer<Throwable> exceptionHandler) {

    this.client = httpClient;
    this.okapiUrl = okapiUrl;
    this.tenantId = tenantId;
    this.token = token;
    this.userId = userId;
    this.requestId = requestId;
    this.exceptionHandler = exceptionHandler;
  }

  public VertxWebClientOkapiHttpClient toWebClient() {
    return VertxWebClientOkapiHttpClient.createClientUsing(client, okapiUrl,
      tenantId, token, userId, requestId);
  }

  public void post(
    URL url,
    Object body,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.postAbs(url.toString(), responseHandler);

    addJsonContentTypeHeader(request);

    addStandardHeaders(request);

    request.setTimeout(5000);

    request.exceptionHandler(this.exceptionHandler::accept);

    if(body != null) {
      //TODO: Catch encoding exceptions here
      String encodedBody = Json.encodePrettily(body);

      log.info("POST {}, Request: {}", url, encodedBody);

      request.end(encodedBody);
    }
    else {
      request.end();
    }
  }

  public void put(
    URL url,
    Object body,
    Handler<HttpClientResponse> responseHandler) {

    put(url.toString(), body, responseHandler);
  }

  public void put(
    String url,
    Object body,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.putAbs(url, responseHandler);

    addJsonContentTypeHeader(request);

    addStandardHeaders(request);

    request.exceptionHandler(this.exceptionHandler::accept);

    //TODO: Catch encoding exceptions here
    String encodedBody = Json.encodePrettily(body);

    log.info("PUT {}, Request: {}", url, encodedBody);

    request.end(encodedBody);
  }

  public void get(
    URL url,
    String query,
    Handler<HttpClientResponse> responseHandler)
    throws MalformedURLException {

    get(new URL(url.getProtocol(), url.getHost(), url.getPort(),
        url.getPath() + "?" + query).toString(), responseHandler);
  }

  public void get(String url, Handler<HttpClientResponse> responseHandler) {
    log.info("GET {}", url);

    HttpClientRequest request = client.getAbs(url, responseHandler);

    addStandardHeaders(request);

    request.exceptionHandler(exceptionHandler::accept);

    request.end();
  }

  public void delete(URL url, Handler<HttpClientResponse> responseHandler) {

    delete(url.toString(), responseHandler);
  }

  public void delete(String url, Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.deleteAbs(url, responseHandler);

    addStandardHeaders(request);

    request.exceptionHandler(this.exceptionHandler::accept);

    request.end();
  }

  private void addStandardHeaders(HttpClientRequest request) {
    addHeaderIfPresent(request, "Accept","application/json, text/plain");
    addHeaderIfPresent(request, OKAPI_URL, okapiUrl.toString());
    addHeaderIfPresent(request, TENANT, this.tenantId);
    addHeaderIfPresent(request, TOKEN, this.token);
    addHeaderIfPresent(request, USER_ID, this.userId);
    addHeaderIfPresent(request, REQUEST_ID, this.requestId);
  }

  private void addJsonContentTypeHeader(HttpClientRequest request) {
    addHeaderIfPresent(request, "Content-type","application/json");
  }

  private void addHeaderIfPresent(
    HttpClientRequest request,
    String name,
    String value) {

    if(StringUtils.isNotBlank(name)) {
      request.headers().add(name, value);
    }
  }
}
