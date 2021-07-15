package org.folio.circulation.resources;

import static org.folio.circulation.support.results.AsynchronousResultBindings.safelyInitialise;

import org.folio.circulation.domain.anonymization.LoanAnonymization;
import org.folio.circulation.domain.anonymization.service.LoansForTenantFinder;
import org.folio.circulation.domain.representations.anonymization.AnonymizeLoansRepresentation;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.CommonFailures;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Perform automatic loan anonymization based on tenant settings for loan history.
 * This process is intended to run in short intervals.
 *
 */
public class ScheduledAnonymizationProcessingResource extends Resource {
  public ScheduledAnonymizationProcessingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/scheduled-anonymize-processing", router)
    .create(this::scheduledAnonymizeLoans);
  }

  private void scheduledAnonymizeLoans(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    ConfigurationRepository configurationRepository = new ConfigurationRepository(clients);
    final var loanRepository = new LoanRepository(clients);
    final var accountRepository = new AccountRepository(clients);

    final var loanAnonymization = new LoanAnonymization(
      loanRepository, accountRepository,
      new AnonymizeStorageLoansRepository(clients),
      new EventPublisher(clients.pubSubPublishingService()));

    final var loansFinder = new LoansForTenantFinder(loanRepository, accountRepository);

    safelyInitialise(configurationRepository::loanHistoryConfiguration)
      .thenCompose(r -> r.after(config -> loanAnonymization.byCurrentTenant(config,
        loansFinder).anonymizeLoans()))
      .thenApply(AnonymizeLoansRepresentation::from)
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenAccept(context::writeResultToHttpResponse);
  }
}
