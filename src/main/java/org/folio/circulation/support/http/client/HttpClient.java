package org.folio.circulation.support.http.client;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.Json;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;

public class HttpClient {

  private static final String TENANT_HEADER = "X-Okapi-Tenant";
  private static final String OKAPI_URL_HEADER = "X-Okapi-Url";

  private final io.vertx.core.http.HttpClient client;
  private final URL okapiUrl;
  private final Consumer<Throwable> exceptionHandler;

  public HttpClient(Vertx vertx,
                    URL okapiUrl,
                    Consumer<Throwable> exceptionHandler) {
    this.client = vertx.createHttpClient();
    this.okapiUrl = okapiUrl;
    this.exceptionHandler = exceptionHandler;
  }

  public void post(URL url,
            Object body,
            String tenantId,
            Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.postAbs(url.toString(), responseHandler);

    request.headers().add("Accept","application/json, text/plain");
    request.headers().add("Content-type","application/json");
    request.headers().add(OKAPI_URL_HEADER, okapiUrl.toString());

    if(tenantId != null) {
      request.headers().add(TENANT_HEADER, tenantId);
    }

    request.setTimeout(3000);

    request.exceptionHandler(exception -> {
      this.exceptionHandler.accept(exception);
    });

    if(body != null) {
      String encodedBody = Json.encodePrettily(body);

      System.out.println(String.format("POST %s, Request: %s",
        url.toString(), encodedBody));

      request.end(encodedBody);
    }
    else {
      request.end();
    }
  }

  public void post(URL url,
                   String tenantId,
                   Handler<HttpClientResponse> responseHandler) {

    post(url, null, tenantId, responseHandler);
  }

  public void get(URL url,
           Handler<HttpClientResponse> responseHandler)
    throws UnsupportedEncodingException {

    get(url, null, responseHandler);
  }

  public void put(URL url,
                  Object body,
                  String tenantId,
                  Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.putAbs(url.toString(), responseHandler);

    request.headers().add("Accept","application/json, text/plain");
    request.headers().add("Content-type","application/json");
    request.headers().add(OKAPI_URL_HEADER, okapiUrl.toString());

    if(tenantId != null) {
      request.headers().add(TENANT_HEADER, tenantId);
    }

    request.end(Json.encodePrettily(body));
  }

  public void get(URL url,
                   String tenantId,
                   Handler<HttpClientResponse> responseHandler) {

    get(url.toString(), tenantId, responseHandler);
  }

  public void get(URL url,
                  String query,
                  String tenantId,
                  Handler<HttpClientResponse> responseHandler)
    throws MalformedURLException {

    get(new URL(url.getProtocol(), url.getHost(), url.getPort(),
        url.getPath() + "?" + query),
      tenantId, responseHandler);
  }

  public void get(String url,
           String tenantId,
           Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.getAbs(url, responseHandler);

    request.headers().add("Accept","application/json");
    request.headers().add(OKAPI_URL_HEADER, okapiUrl.toString());

    if(tenantId != null) {
      request.headers().add(TENANT_HEADER, tenantId);
    }

    request.end();
  }

  public void delete(URL url,
              String tenantId,
              Handler<HttpClientResponse> responseHandler) {

    delete(url.toString(), tenantId, responseHandler);
  }

  public void delete(String url,
              String tenantId,
              Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.deleteAbs(url, responseHandler);

    request.headers().add("Accept","application/json, text/plain");
    request.headers().add(OKAPI_URL_HEADER, okapiUrl.toString());

    if(tenantId != null) {
      request.headers().add(TENANT_HEADER, tenantId);
    }

    request.end();
  }
}
