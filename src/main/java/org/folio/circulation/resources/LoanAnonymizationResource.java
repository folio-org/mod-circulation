package org.folio.circulation.resources;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.anonymization.DefaultLoanAnonymizationService;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersService;
import org.folio.circulation.domain.anonymization.service.LoansForBorrowerFinder;
import org.folio.circulation.domain.representations.anonymization.AnonymizeLoansRepresentation;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class LoanAnonymizationResource extends Resource {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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

    final var loanRepository = new LoanRepository(clients, new ItemRepository(clients),
      new UserRepository(clients));
    final var accountRepository = new AccountRepository(clients);

    final var loansFinder = new LoansForBorrowerFinder(borrowerId,
      loanRepository, accountRepository);

    final var anonymizeStorageLoansRepository = new AnonymizeStorageLoansRepository(clients);
    final var eventPublisher = new EventPublisher(clients.pubSubPublishingService());

    final var loanAnonymizationService = new DefaultLoanAnonymizationService(
      new AnonymizationCheckersService(), anonymizeStorageLoansRepository, eventPublisher);

    log.info("anonymizeLoans:: initializing loan anonymization for borrower: {}", borrowerId);

    loanAnonymizationService.anonymizeLoans(loansFinder::findLoansToAnonymize)
      .thenApply(AnonymizeLoansRepresentation::from)
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
