package org.folio.circulation.support.http.server;

import static java.util.stream.Collectors.toMap;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.REQUEST_ID;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.http.OkapiHeader.TOKEN;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.folio.circulation.support.InvalidOkapiLocationException;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.VertxWebClientOkapiHttpClient;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.RoutingContext;

public class WebContext {
  private final RoutingContext routingContext;
  private final Map<String, String> detachedHeaders;

  public WebContext(RoutingContext routingContext) {
    this.routingContext = routingContext;
    this.detachedHeaders = null;
  }

  public WebContext(Map<String, String> detachedHeaders) {
    this.routingContext = null;

    Map<String, String> normalizedHeaders = new HashMap<>();

    if (detachedHeaders != null) {
      detachedHeaders.forEach((key, value) -> {
        if (key != null && value != null) {
          normalizedHeaders.put(key.toLowerCase(Locale.ROOT), value);
        }
      });
    }

    this.detachedHeaders = Map.copyOf(normalizedHeaders);
  }

  public String getTenantId() {
    return getHeader(TENANT);
  }

  public String getOkapiToken() {
    return getHeader(TOKEN);
  }

  public String getUserId() {
    // If there is no user-id header, than it is important to return null,
    // otherwise when a record is created/updated metadata will be broken
    return getHeader(USER_ID);
  }

  public String getOkapiLocation() {
    return getHeader(OKAPI_URL);
  }

  public String getRequestId() {
    return getHeader(REQUEST_ID);
  }

  private String getHeader(String header) {
    if (routingContext != null) {
      return routingContext.request().getHeader(header);
    }

    return detachedHeaders.get(header.toLowerCase(Locale.ROOT));
  }

  public Integer getIntegerParameter(String name, Integer defaultValue) {
    if (routingContext == null) {
      return defaultValue;
    }

    String value = routingContext.request().getParam(name);

    return value != null ? Integer.parseInt(value) : defaultValue;
  }

  public String getStringParameter(String name, String defaultValue) {
    if (routingContext == null) {
      return defaultValue;
    }

    String value = routingContext.request().getParam(name);

    return value != null ? value : defaultValue;
  }

  public String getStringParameter(String name) {
    return getStringParameter(name, null);
  }

  public URL getOkapiBasedUrl(String path) throws MalformedURLException {
    URL currentRequestUrl = new URL(getOkapiLocation());

    return new URL(currentRequestUrl.getProtocol(), currentRequestUrl.getHost(),
      currentRequestUrl.getPort(), path);
  }

  public OkapiHttpClient createHttpClient(HttpClient httpClient) {
    return createHttpClient(httpClient, getTenantId());
  }

  public OkapiHttpClient createHttpClient(HttpClient httpClient, String tenantId) {
    URL okapiUrl;

    try {
      okapiUrl = new URL(getOkapiLocation());
    }
    catch(MalformedURLException e) {
      throw new InvalidOkapiLocationException(getOkapiLocation(), e);
    }

    return VertxWebClientOkapiHttpClient.createClientUsing(httpClient,
      okapiUrl, tenantId, getOkapiToken(), getUserId(),
      getRequestId());
  }

  public void write(HttpResponse response) {
    response.writeTo(requireRoutingContext().response());
  }

  public void writeResultToHttpResponse(Result<HttpResponse> result) {
    result.applySideEffect(this::write, this::write);
  }

  public Map<String, String> getHeaders() {
    if (routingContext == null) {
      return detachedHeaders;
    }

    return routingContext.request().headers().entries().stream()
      .collect(toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue, (a, b) -> b));
  }

  private RoutingContext requireRoutingContext() {
    if (routingContext == null) {
      throw new IllegalStateException("RoutingContext is not available for detached WebContext");
    }

    return routingContext;
  }
}
