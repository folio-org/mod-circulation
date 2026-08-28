package org.folio.circulation.resources.handlers;

import static org.folio.circulation.support.http.server.NoContentResponse.noContent;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.services.events.FeeFineBalanceChangedEventProcessor;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class FeeFineBalanceChangedHandlerResource extends Resource {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public FeeFineBalanceChangedHandlerResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/handlers/fee-fine-balance-changed", router)
      .create(this::handleFeeFineBalanceChangedEvent);
  }

  private void handleFeeFineBalanceChangedEvent(RoutingContext routingContext) {
    log.info("handleFeeFineBalanceChangedEvent:: handling event: {}",
      routingContext.body().asJsonObject());
    final WebContext context = new WebContext(routingContext);
    final var processor = new FeeFineBalanceChangedEventProcessor();

    processor.process(routingContext.body().asJsonObject(), context, client)
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(result -> result.applySideEffect(context::write, failure -> {
        log.error("handleFeeFineBalanceChangedEvent:: cannot handle event {}, error occurred {}",
          routingContext.body().asJsonObject(), failure);
        context.write(noContent());
      }));
  }
}
