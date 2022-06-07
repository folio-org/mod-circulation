package org.folio.circulation.resources.agedtolost;

import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;

import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.services.agedtolost.ChargeLostFeesWhenAgedToLostService;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.response.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ScheduledAgeToLostFeeChargingResource extends Resource {
  public ScheduledAgeToLostFeeChargingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/scheduled-age-to-lost-fee-charging", router)
      .create(this::scheduledAgeToLostFeeCharging);
  }

  private void scheduledAgeToLostFeeCharging(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final var clients = create(context, client);
    final ChargeLostFeesWhenAgedToLostService chargingService =
      new ChargeLostFeesWhenAgedToLostService(clients, new ItemRepository(clients),
        new UserRepository(clients));

    chargingService.chargeFees()
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
