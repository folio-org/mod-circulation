package org.folio.circulation.resources.handlers;

import static org.folio.circulation.domain.EventType.LOAN_RELATED_FEE_FINE_CLOSED;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.services.events.LoanRelatedFeeFineClosedEventProcessor;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.CommonFailures;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class LoanRelatedFeeFineClosedHandlerResource extends Resource {
  private static final Logger log = LogManager.getLogger(
    LoanRelatedFeeFineClosedHandlerResource.class);

  public LoanRelatedFeeFineClosedHandlerResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/handlers/loan-related-fee-fine-closed", router)
      .create(this::handleFeeFineClosedEvent);
  }

  private void handleFeeFineClosedEvent(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final var processor = new LoanRelatedFeeFineClosedEventProcessor();

    log.info("Event {} received: {}", LOAN_RELATED_FEE_FINE_CLOSED, routingContext.body().asString());

    processor.process(routingContext.body().asJsonObject(), context, client)
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(result -> result.applySideEffect(context::write, failure -> {
        log.error("Cannot handle event [{}], error occurred {}",
          routingContext.body().asString(), failure);

        context.write(noContent());
      }));
  }
}
