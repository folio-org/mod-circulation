package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import org.folio.circulation.domain.anonymization.LoanAnonymization;
import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.domain.anonymization.LoanAnonymizationService;
import org.folio.circulation.domain.representations.anonymization.AnonymizeLoansRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class LoanAnonymizationResource extends Resource {

  public LoanAnonymizationResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
        "/loan-anonymization/anonymizeByUserId/:userId", router);
    routeRegistration.create(this::anonymizeLoans);
  }

  private void anonymizeLoans(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    String borrowerId = routingContext.request()
      .getParam("userId");
    String tenant = routingContext.request()
      .getHeader(TENANT);

    LoanAnonymizationService loanAnonymizationService = LoanAnonymization
        .newLoanAnonymizationService(clients);

    completedFuture(new LoanAnonymizationRecords(borrowerId, tenant))
      .thenCompose(loanAnonymizationService::anonymizeLoans)
      .thenApply(AnonymizeLoansRepresentation::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }
}
