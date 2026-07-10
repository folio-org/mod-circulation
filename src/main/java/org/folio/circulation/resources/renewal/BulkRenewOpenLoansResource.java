package org.folio.circulation.resources.renewal;

import org.folio.circulation.resources.Resource;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class BulkRenewOpenLoansResource extends Resource {
  // Task 1 establishes the shared guard seam; later tasks will use it for background work.
  private static final BulkRenewalJobGuard DEFAULT_JOB_GUARD = new BulkRenewalJobGuard();

  private final BulkRenewalJobGuard jobGuard;
  private final BulkRenewOpenLoansService service;

  public BulkRenewOpenLoansResource(HttpClient client) {
    this(client, DEFAULT_JOB_GUARD);
  }

  BulkRenewOpenLoansResource(HttpClient client, BulkRenewalJobGuard jobGuard) {
    this(client, jobGuard, new BulkRenewOpenLoansService(jobGuard, client));
  }

  BulkRenewOpenLoansResource(HttpClient client, BulkRenewalJobGuard jobGuard,
    BulkRenewOpenLoansService service) {

    super(client);
    this.jobGuard = jobGuard;
    this.service = service;
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/bulk-renew-open-loans", router)
      .create(this::bulkRenewOpenLoans);
  }

  private void bulkRenewOpenLoans(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    BulkRenewalWebContext detachedContext = new BulkRenewalWebContext(context.getHeaders());

    if (!service.trigger(detachedContext)) {
      context.write(new BadRequestFailure("bulk renewal job already running"));
      return;
    }

    context.write(NoContentResponse.noContent());
  }
}
