package org.folio.circulation.support;

import java.net.MalformedURLException;
import java.net.URL;

import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.ext.web.RoutingContext;

public final class ClientUtil {

  public static OkapiHttpClient createHttpClient(RoutingContext routingContext,
                                           WebContext context)
    throws MalformedURLException {

    return new OkapiHttpClient(routingContext.vertx().createHttpClient(),
      new URL(context.getOkapiLocation()), context.getTenantId(),
      context.getOkapiToken(),
      exception -> ServerErrorResponse.internalError(routingContext.response(),
        String.format("Failed to contact storage module: %s",
          exception.toString())));
  }

  public static CollectionResourceClient createClient(
      OkapiHttpClient client,
      WebContext context,
      String path)
          throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl(path),
      context.getTenantId());
  }

  public static CollectionResourceClient createLoanRulesClient(
      OkapiHttpClient client,
      WebContext context)
          throws MalformedURLException {
    return createClient(client, context, "/loan-rules-storage");
  }

  public static CollectionResourceClient createPoliciesClient(
      OkapiHttpClient client,
      WebContext context)
          throws MalformedURLException {
    return createClient(client, context, "/loan-policy-storage/loan-policies/");
  }

  public static CollectionResourceClient getLoanRulesClient(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      return createLoanRulesClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));
      return null;
    }
  }

  public static CollectionResourceClient getPoliciesClient(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      return createPoliciesClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));
      return null;
    }
  }
}
