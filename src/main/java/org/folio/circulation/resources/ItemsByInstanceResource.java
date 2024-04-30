package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.InstanceExtended;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import java.lang.invoke.MethodHandles;

public class ItemsByInstanceResource extends Resource {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public ItemsByInstanceResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/items-by-instance", router);

    routeRegistration.get(this::getInstanceItems);
  }

  private void getInstanceItems(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    String instanceId = routingContext.pathParam("id");
    log.debug("getInstanceItems:: instanceId: " + instanceId);
    final var itemRepository = new ItemRepository(clients);
    itemRepository.getInstanceWithItems(instanceId)
      .thenApply(r -> r.map(InstanceExtended::toJson))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
