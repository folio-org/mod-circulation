package org.folio.circulation.support.http.client;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.Json;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OkapiHttpClient {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String TENANT_HEADER = "X-Okapi-Tenant";
  private static final String TOKEN_HEADER = "X-Okapi-Token";
  private static final String OKAPI_URL_HEADER = "X-Okapi-Url";

  private final HttpClient client;
  private final URL okapiUrl;
  private final String tenantId;
  private final String token;
  private final Consumer<Throwable> exceptionHandler;

  public OkapiHttpClient(HttpClient httpClient,
                         URL okapiUrl,
                         String tenantId,
                         String token,
                         Consumer<Throwable> exceptionHandler) {

    this.client = httpClient;
    this.okapiUrl = okapiUrl;
    this.tenantId = tenantId;
    this.token = token;
    this.exceptionHandler = exceptionHandler;
  }

  public void post(URL url,
                   Object body,
                   Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.postAbs(url.toString(), responseHandler);

    request.headers().add("Accept","application/json, text/plain");
    request.headers().add("Content-type","application/json");
    request.headers().add(OKAPI_URL_HEADER, okapiUrl.toString());

    addMandatoryHeaders(request);

    request.setTimeout(5000);

    request.exceptionHandler(exception -> {
      this.exceptionHandler.accept(exception);
    });

    if(body != null) {
      String encodedBody = Json.encodePrettily(body);

      log.info("POST {}, Request: {}", url, encodedBody);

      request.end(encodedBody);
    }
    else {
      request.end();
    }
  }

  public void put(URL url,
                  Object body,
                  Handler<HttpClientResponse> responseHandler) {

    put(url.toString(), body, responseHandler);
  }

  public void put(String url,
                  Object body,
                  Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.putAbs(url, responseHandler);

    request.headers().add("Accept","application/json, text/plain");
    request.headers().add("Content-type","application/json");
    request.headers().add(OKAPI_URL_HEADER, okapiUrl.toString());

    addMandatoryHeaders(request);

    request.exceptionHandler(exception -> {
      this.exceptionHandler.accept(exception);
    });

    String encodedBody = Json.encodePrettily(body);

    log.info("PUT {}, Request: {}", url, encodedBody);

    request.end(encodedBody);
  }

  public void get(URL url, Handler<HttpClientResponse> responseHandler) {

    get(url.toString(), responseHandler);
  }

  public void get(URL url,
                  String query,
                  Handler<HttpClientResponse> responseHandler)
    throws MalformedURLException {

    get(new URL(url.getProtocol(), url.getHost(), url.getPort(),
        url.getPath() + "?" + query),
      responseHandler);
  }

  public void get(String url, Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.getAbs(url, responseHandler);

    request.headers().add("Accept","application/json");
    request.headers().add(OKAPI_URL_HEADER, okapiUrl.toString());

    addMandatoryHeaders(request);

    request.exceptionHandler(exception -> {
      this.exceptionHandler.accept(exception);
    });

    request.end();
  }

  public void delete(URL url, Handler<HttpClientResponse> responseHandler) {

    delete(url.toString(), responseHandler);
  }

  public void delete(String url, Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.deleteAbs(url, responseHandler);

    request.headers().add("Accept","application/json, text/plain");
    request.headers().add(OKAPI_URL_HEADER, okapiUrl.toString());

    addMandatoryHeaders(request);

    request.exceptionHandler(exception -> {
      this.exceptionHandler.accept(exception);
    });

    request.end();
  }

  private void addMandatoryHeaders(HttpClientRequest request) {
    if(this.tenantId != null && this.tenantId.trim() != "") {
      request.headers().add(TENANT_HEADER, this.tenantId);
    }

    if(this.token != null && this.token.trim() != "") {
      request.headers().add(TOKEN_HEADER, this.token);
    }
  }
}
