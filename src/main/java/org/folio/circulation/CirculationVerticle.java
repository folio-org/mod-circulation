package org.folio.circulation;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.resources.*;
import org.folio.circulation.support.logging.Logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class CirculationVerticle extends AbstractVerticle {
  private HttpServer server;

  @Override
  public void start(Future<Void> startFuture) {
    Logging.initialiseFormat();

    final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    log.info("Starting circulation module");

    Router router = Router.router(vertx);

    // bump up the connection pool size from the default value of 5
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(100));

    this.server = vertx.createHttpServer();

    new CheckOutByBarcodeResource("/circulation/check-out-by-barcode",
      client, new RegularCheckOutStrategy()).register(router);
    new CheckOutByBarcodeResource("/circulation/override-check-out-by-barcode",
      client, new OverrideCheckOutStrategy()).register(router);
    new CheckInByBarcodeResource(client).register(router);

    new RenewByBarcodeResource("/circulation/renew-by-barcode",
      new RegularRenewalStrategy(), client).register(router);
    new RenewByIdResource("/circulation/renew-by-id",
      new RegularRenewalStrategy(), client).register(router);
    new RenewByBarcodeResource("/circulation/override-renewal-by-barcode",
      new OverrideRenewalStrategy(), client).register(router);

    new LoanCollectionResource(client).register(router);
    new RequestCollectionResource(client).register(router);
    new RequestQueueResource(client).register(router);
    new RequestByInstanceIdResource(client).register(router);

    new RequestHoldShelfClearanceResource("/circulation/requests-reports/hold-shelf-clearance/:servicePointId", client)
      .register(router);
    new ItemsInTransitResource("/inventory-reports/items-in-transit", client)
      .register(router);
    new PickSlipsResource("/circulation/pick-slips/:servicePointId", client)
        .register(router);

    new CirculationRulesResource("/circulation/rules", client)
      .register(router);
    new LoanCirculationRulesEngineResource(
      "/circulation/rules/loan-policy",
      "/circulation/rules/loan-policy-all",
       client)
        .register(router);
    new OverdueFineCirculationRulesEngineResource(
            "/circulation/rules/overdue-fine-policy",
            "/circulation/rules/overdue-fine-policy-all",
            client)
            .register(router);
    new LostItemCirculationRulesEngineResource(
            "/circulation/rules/lost-item-policy",
            "/circulation/rules/lost-item-policy-all",
            client)
            .register(router);
    new RequestCirculationRulesEngineResource(
      "/circulation/rules/request-policy",
      "/circulation/rules/request-policy-all",
       client)
        .register(router);
    new NoticeCirculationRulesEngineResource(
      "/circulation/rules/notice-policy",
      "/circulation/rules/notice-policy-all",
        client)
        .register(router);

    new DueDateScheduledNoticeProcessingResource(client).register(router);
    new DueDateNotRealTimeScheduledNoticeProcessingResource(client).register(router);
    new RequestScheduledNoticeProcessingResource(client).register(router);
    new ExpiredSessionProcessingResource(client).register(router);

    new LoanAnonymizationResource(client).register(router);
    new DeclareLostResource(client).register(router);
    new ScheduledAnonymizationProcessingResource(client).register(router);

    new EndPatronActionSessionResource(client).register(router);

    server.requestHandler(router::accept)
      .listen(config().getInteger("port"), result -> {
        if (result.succeeded()) {
          log.info("Listening on {}", server.actualPort());
          startFuture.complete();
        } else {
          startFuture.fail(result.cause());
        }
      });
  }

  @Override
  public void stop(Future<Void> stopFuture) {
    final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    log.info("Stopping circulation module");

    if(server != null) {
      server.close(result -> {
        if (result.succeeded()) {
          log.info("Stopped listening on {}", server.actualPort());
          stopFuture.complete();
        } else {
          stopFuture.fail(result.cause());
        }
      });
    }
  }
}
