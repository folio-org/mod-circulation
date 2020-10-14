package org.folio.circulation.resources.agedtolost;

import static org.folio.circulation.support.Clients.create;

import org.folio.circulation.resources.Resource;
import org.folio.circulation.services.agedtolost.ChargeLostFeesWhenAgedToLostService;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
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
    final ChargeLostFeesWhenAgedToLostService chargingService =
      new ChargeLostFeesWhenAgedToLostService(create(context, client));

    chargingService.chargeFees()
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
