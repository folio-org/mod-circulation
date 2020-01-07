package org.folio.circulation.support.http.client;

import java.net.URL;

import io.vertx.core.http.HttpClient;

public class OkapiHttpClient {
  private final HttpClient client;
  private final URL okapiUrl;
  private final String tenantId;
  private final String token;
  private final String userId;
  private final String requestId;

  public OkapiHttpClient(HttpClient httpClient, URL okapiUrl, String tenantId,
    String token, String userId, String requestId) {

    this.client = httpClient;
    this.okapiUrl = okapiUrl;
    this.tenantId = tenantId;
    this.token = token;
    this.userId = userId;
    this.requestId = requestId;
  }

  public VertxWebClientOkapiHttpClient toWebClient() {
    return VertxWebClientOkapiHttpClient.createClientUsing(client, okapiUrl,
      tenantId, token, userId, requestId);
  }
}
