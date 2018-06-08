package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.LoanRenewalService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.WebContext;

public class RenewByIdResource extends Resource {
  public RenewByIdResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/renew-by-id", router);

    routeRegistration.create(this::renew);
  }

  private void renew(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final LoanRenewalService loanRenewalService = LoanRenewalService.using(clients);

    //TODO: Validation check for same user should be in the domain service

    RenewByIdRequest.from(routingContext.getBodyAsJson())
      .after(loanRepository::findOpenLoanById)
      .thenComposeAsync(r -> r.after(loanRenewalService::renew))
      .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(this::toRenewedLoanResponse)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private WritableHttpResult<JsonObject> toRenewedLoanResponse(HttpResult<JsonObject> result) {
    //TODO: Extract and clean up this check for writable http result
    if(result.failed()) {
      return HttpResult.failure(result.cause());
    }
    else {
      return new OkJsonHttpResult(result.value(),
        String.format("/circulation/loans/%s", result.value().getString("id")));
    }
  }
}
