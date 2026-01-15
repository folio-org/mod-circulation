package org.folio.circulation.resources;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Perform automatic loan anonymization based on tenant settings for request
 * This process is intended to run in short intervals.
 */
public class ScheduledRequestAnonymizationProcessingResource extends Resource {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public ScheduledRequestAnonymizationProcessingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/scheduled-request-anonymize-processing", router)
      .create(this::scheduledAnonymizeRequest);
  }

  private void scheduledAnonymizeRequest(RoutingContext routingContext) {
// implement the request anonymization process here
  }
}
