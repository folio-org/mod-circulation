package org.folio.circulation.support.http.server;

import io.vertx.ext.web.RoutingContext;

import java.net.MalformedURLException;
import java.net.URL;

public class WebContext {
  private final RoutingContext routingContext;

  WebContext(RoutingContext routingContext) {
    this.routingContext = routingContext;
  }

  String getTenantId() {
    return getHeader("X-Okapi-Tenant", "");
  }

  String getOkapiLocation() {
    return getHeader("X-Okapi-Url", "");
  }

  String getHeader(String header) {
    return routingContext.request().getHeader(header);
  }

  String getHeader(String header, String defaultValue) {
    return hasHeader(header) ? getHeader(header) : defaultValue;
  }

  boolean hasHeader(String header) {
    return routingContext.request().headers().contains(header);
  }

  public URL getOkapiBasedUrl(String path)
    throws MalformedURLException {

    URL currentRequestUrl = new URL(getOkapiLocation());

    return new URL(currentRequestUrl.getProtocol(), currentRequestUrl.getHost(),
      currentRequestUrl.getPort(), path);
  }
}
