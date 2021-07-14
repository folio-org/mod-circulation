package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;

import org.folio.circulation.domain.anonymization.LoanAnonymization;
import org.folio.circulation.domain.representations.anonymization.AnonymizeLoansRepresentation;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
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
    RouteRegistration routeRegistration = new RouteRegistration("/loan-anonymization/by-user/:userId", router);
    routeRegistration.create(this::anonymizeLoans);
  }

  private void anonymizeLoans(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    String borrowerId = routingContext.request().getParam("userId");

    final var loanAnonymization = new LoanAnonymization(
      new LoanRepository(clients), new AccountRepository(clients),
      new AnonymizeStorageLoansRepository(clients),
      new EventPublisher(clients.pubSubPublishingService()));

    completedFuture(loanAnonymization.byUserId(borrowerId).anonymizeLoans()
      .thenApply(AnonymizeLoansRepresentation::from)
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse));
  }
}
