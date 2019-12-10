package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class VertxWebClientOkapiHttpClient {
  private final WebClient webClient;
  private final URL okapiUrl;
  private String tenantId;

  public static VertxWebClientOkapiHttpClient createClientUsing(
    HttpClient httpClient, URL okapiUrl, String tenantId) {

    return new VertxWebClientOkapiHttpClient(WebClient.wrap(httpClient),
      okapiUrl, tenantId);
  }

  private VertxWebClientOkapiHttpClient(WebClient webClient, URL okapiUrl,
    String tenantId) {

    this.webClient = webClient;
    this.okapiUrl = okapiUrl;
    this.tenantId = tenantId;
  }

  public CompletableFuture<Result<Response>> get(String url) {
    final CompletableFuture<Result<Response>> futureResponse = new CompletableFuture<>();

    webClient
      .getAbs(url)
      .putHeader("Accept","application/json, text/plain")
      .putHeader(OKAPI_URL, okapiUrl.toString())
      .putHeader(TENANT, this.tenantId)
//      .putHeader(TOKEN, this.token)
//      .putHeader(USER_ID, this.userId)
//      .putHeader(REQUEST_ID, this.requestId)
      .timeout(5000)
      .send(ar -> {
        if (ar.succeeded()) {
          final HttpResponse<Buffer> response = ar.result();

          futureResponse.complete(Result.succeeded(new Response(
            response.statusCode(), response.bodyAsString(), "NOT PARSED YET",
            new CaseInsensitiveHeaders(), url)));
        }
        else {
          futureResponse.complete(Result.failed(new ServerErrorFailure(ar.cause())));
        }
      });

    return futureResponse;
  }
}
