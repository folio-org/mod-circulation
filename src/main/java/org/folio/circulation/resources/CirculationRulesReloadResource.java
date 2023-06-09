package org.folio.circulation.resources;

import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.CommonFailures;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Write and read the circulation rules.
 */
public class CirculationRulesReloadResource extends Resource {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final String rootPath;

  /**
   * Set the URL path.
   * @param rootPath  URL path
   * @param client HTTP client
   */
  public CirculationRulesReloadResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  /**
   * Register the path set in the constructor.
   * @param router  where to register
   */
  @Override
  public void register(Router router) {
    router.post(rootPath).handler(this::reload);
  }

  private void reload(RoutingContext routingContext) {
    log.debug("reload:: parameters: routingContext: Starting reload of circulation rules.");
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    CollectionResourceClient circulationRulesClient = clients.circulationRulesStorage();  
    CirculationRulesCache.getInstance().reloadRules(context.getTenantId(), circulationRulesClient)
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenAccept(context::writeResultToHttpResponse);
  }
}
