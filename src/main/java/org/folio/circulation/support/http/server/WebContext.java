package org.folio.circulation.support.http.server;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.REQUEST_ID;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.http.OkapiHeader.TOKEN;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;

import java.net.MalformedURLException;
import java.net.URL;

import org.folio.circulation.support.InvalidOkapiLocationException;
import org.folio.circulation.support.http.client.VertxWebClientOkapiHttpClient;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.RoutingContext;

public class WebContext {
  private final RoutingContext routingContext;

  public WebContext(RoutingContext routingContext) {
    this.routingContext = routingContext;
  }

  public String getTenantId() {
    return getHeader(TENANT, "");
  }

  public String getOkapiToken() {
    return getHeader(TOKEN, "");
  }

  public String getUserId() {
    return getHeader(USER_ID, "");
  }

  public String getOkapiLocation() {
    return getHeader(OKAPI_URL, "");
  }

  public String getRequestId() {
    return getHeader(REQUEST_ID, "");
  }

  private String getHeader(String header) {
    return routingContext.request().getHeader(header);
  }

  private String getHeader(String header, String defaultValue) {
    return hasHeader(header) ? getHeader(header) : defaultValue;
  }

  private boolean hasHeader(String header) {
    return routingContext.request().headers().contains(header);
  }

  public Integer getIntegerParameter(String name, Integer defaultValue) {
    String value = routingContext.request().getParam(name);

    return value != null ? Integer.parseInt(value) : defaultValue;
  }

  public String getStringParameter(String name, String defaultValue) {
    String value = routingContext.request().getParam(name);

    return value != null ? value : defaultValue;
  }

  public URL getOkapiBasedUrl(String path)
    throws MalformedURLException {

    URL currentRequestUrl = new URL(getOkapiLocation());

    return new URL(currentRequestUrl.getProtocol(), currentRequestUrl.getHost(),
      currentRequestUrl.getPort(), path);
  }

  public VertxWebClientOkapiHttpClient createHttpClient(HttpClient httpClient) {
    URL okapiUrl;

    try {
      okapiUrl = new URL(getOkapiLocation());
    }
    catch(MalformedURLException e) {
      throw new InvalidOkapiLocationException(getOkapiLocation(), e);
    }

    return VertxWebClientOkapiHttpClient.createClientUsing(httpClient,
      okapiUrl, getTenantId(), getOkapiToken(), getUserId(),
      getRequestId());
  }
}
