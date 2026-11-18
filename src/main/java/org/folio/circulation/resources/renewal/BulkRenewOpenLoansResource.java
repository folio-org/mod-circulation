package org.folio.circulation.resources.renewal;

import static org.folio.circulation.support.http.OkapiHeader.TOKEN;

import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class BulkRenewOpenLoansResource extends Resource {
  private static final Logger log = LogManager.getLogger(BulkRenewOpenLoansResource.class);

  private static final BulkRenewalJobGuard DEFAULT_JOB_GUARD = new BulkRenewalJobGuard();

  private final BulkRenewOpenLoansService service;

  public BulkRenewOpenLoansResource(HttpClient client) {
    this(client, DEFAULT_JOB_GUARD);
  }

  BulkRenewOpenLoansResource(HttpClient client, BulkRenewalJobGuard jobGuard) {
    this(client, BulkRenewOpenLoansService.forHttpClient(jobGuard, client));
  }

  BulkRenewOpenLoansResource(HttpClient client, BulkRenewOpenLoansService service) {
    super(client);
    this.service = service;
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/bulk-renew-open-loans", router)
      .create(this::bulkRenewOpenLoans);
  }

  private void bulkRenewOpenLoans(RoutingContext routingContext) {
    var headersAsLogValue = inboundHeadersAsString(routingContext);
    log.info("bulk renewal inbound request headers={}", headersAsLogValue);

    final WebContext context = new WebContext(routingContext);
    BulkRenewalWebContext detachedContext = new BulkRenewalWebContext(
      context.getHeaders(), context.getVertxContext());

    if (!service.trigger(detachedContext)) {
      context.write(new BadRequestFailure("bulk renewal job already running"));
      return;
    }

    context.write(NoContentResponse.noContent());
  }

  private String inboundHeadersAsString(RoutingContext routingContext) {
    return routingContext.request().headers().entries().stream()
      .filter(entry -> !TOKEN.equalsIgnoreCase(entry.getKey()))
      .map(entry -> entry.getKey().toLowerCase(Locale.ROOT) + "=" + entry.getValue())
      .toList()
      .toString();
  }
}
