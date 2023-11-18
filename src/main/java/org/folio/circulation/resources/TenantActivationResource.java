package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.JsonHttpResponse.created;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.services.PubSubRegistrationService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TenantActivationResource extends Resource {

  public TenantActivationResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/_/tenant", router);
    routeRegistration.create(this::enableModuleForTenant);
    routeRegistration.deleteAll(ctx -> noContent().writeTo(ctx.response()));
  }

  private void enableModuleForTenant(RoutingContext routingContext) {
    WebContext webContext = new WebContext(routingContext);
    Clients clients = Clients.create(webContext, client);
    Map<String, String> headers = webContext.getHeaders();
    PubSubRegistrationService.registerModule(headers, routingContext.vertx())
      .thenCompose(ignored -> warmUpCirculationRulesCache(webContext, clients))
      .thenRun(() -> created(new JsonObject()).writeTo(routingContext.response()))
      .exceptionally(throwable -> {
        ServerErrorResponse.internalError(routingContext.response(), throwable.getLocalizedMessage());
        return null;
      });
  }

  private CompletableFuture<Void> warmUpCirculationRulesCache(WebContext context, Clients clients) {
    log.info("warmUpCirculationRulesCache:: warming up circulation rules cache");

    return CirculationRulesCache.getInstance()
      .reloadRules(context.getTenantId(), clients.circulationRulesStorage())
      .thenAccept(r -> r.applySideEffect(
        rules -> log.info("warmUpCirculationRulesCache:: warm-up complete"),
        failure -> log.error("warmUpCirculationRulesCache:: warm-up failed: {}", failure)
      ));
  }
}
