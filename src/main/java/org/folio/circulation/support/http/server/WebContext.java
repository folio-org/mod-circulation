package org.folio.circulation.support.http.server;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.support.InvalidOkapiLocationException;
import org.folio.circulation.support.http.client.OkapiHttpClient;

import java.net.MalformedURLException;
import java.net.URL;

import static org.folio.circulation.support.http.OkapiHeader.*;

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

  public String getOkapiUserId() {
    return getHeader(USER_ID, "");
  }

  public String getOkapiLocation() {
    return getHeader(OKAPI_URL, "");
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

  public OkapiHttpClient createHttpClient() {
    return createHttpClient(routingContext.vertx().createHttpClient());
  }

  public OkapiHttpClient createHttpClient(HttpClient httpClient) {
    URL okapiUrl;

    try {
      okapiUrl = new URL(getOkapiLocation());
    }
    catch(MalformedURLException e) {
      throw new InvalidOkapiLocationException(getOkapiLocation(), e);
    }

    return new OkapiHttpClient(httpClient,
      okapiUrl, getTenantId(), getOkapiToken(), getOkapiUserId(),
      exception -> ServerErrorResponse.internalError(routingContext.response(),
        String.format("Failed to contact storage module: %s",
          exception.toString())));
  }
}
