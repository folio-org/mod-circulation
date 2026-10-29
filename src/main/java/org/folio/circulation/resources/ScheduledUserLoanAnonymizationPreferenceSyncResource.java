package org.folio.circulation.resources;

import static org.folio.circulation.support.http.client.PageLimit.oneThousand;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.anonymization.service.UserLoanAnonymizationPreferenceSyncService;
import org.folio.circulation.infrastructure.storage.loans.UserLoanAnonymizationPreferenceRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Nightly resync of the users' loan anonymization preferences, runs whether or not user-specific
 * loan anonymization is enabled.
 */
public class ScheduledUserLoanAnonymizationPreferenceSyncResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public ScheduledUserLoanAnonymizationPreferenceSyncResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/scheduled-loan-anonymization-preference-sync", router)
      .create(this::syncPreferences);
  }

  private void syncPreferences(RoutingContext routingContext) {
    log.debug("syncPreferences:: triggered");
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    new UserLoanAnonymizationPreferenceSyncService(new UserRepository(clients),
        new UserLoanAnonymizationPreferenceRepository(clients), oneThousand())
      .sync()
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
