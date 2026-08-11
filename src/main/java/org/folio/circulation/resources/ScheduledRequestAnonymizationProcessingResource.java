package org.folio.circulation.resources;

import org.folio.circulation.support.RouteRegistration;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Perform automatic loan anonymization based on tenant settings for request
 * This process is intended to run in short intervals.
 */
public class ScheduledRequestAnonymizationProcessingResource extends Resource {
  public ScheduledRequestAnonymizationProcessingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/scheduled-request-anonymize-processing", router)
      .create(this::scheduledAnonymizeRequest);
  }

  private void scheduledAnonymizeRequest(RoutingContext routingContext) {
    routingContext.response()
      .setStatusCode(200)
      .putHeader("content-length", "0")
      .end();
  }
}
