package org.folio.circulation;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.resources.ChangeDueDateResource;
import org.folio.circulation.resources.CheckInByBarcodeResource;
import org.folio.circulation.resources.CheckOutByBarcodeResource;
import org.folio.circulation.resources.CirculationRulesResource;
import org.folio.circulation.resources.ClaimItemReturnedResource;
import org.folio.circulation.resources.DeclareClaimedReturnedItemAsMissingResource;
import org.folio.circulation.resources.DeclareLostResource;
import org.folio.circulation.resources.DueDateNotRealTimeScheduledNoticeProcessingResource;
import org.folio.circulation.resources.DueDateScheduledNoticeProcessingResource;
import org.folio.circulation.resources.EndPatronActionSessionResource;
import org.folio.circulation.resources.ExpiredSessionProcessingResource;
import org.folio.circulation.resources.FeeFineScheduledNoticeProcessingResource;
import org.folio.circulation.resources.ItemsInTransitResource;
import org.folio.circulation.resources.ItemsLostRequiringActualCostResource;
import org.folio.circulation.resources.LoanAnonymizationResource;
import org.folio.circulation.resources.LoanCirculationRulesEngineResource;
import org.folio.circulation.resources.LoanCollectionResource;
import org.folio.circulation.resources.LostItemCirculationRulesEngineResource;
import org.folio.circulation.resources.NoticeCirculationRulesEngineResource;
import org.folio.circulation.resources.OverdueFineCirculationRulesEngineResource;
import org.folio.circulation.resources.OverrideCheckOutStrategy;
import org.folio.circulation.resources.PickSlipsResource;
import org.folio.circulation.resources.RegularCheckOutStrategy;
import org.folio.circulation.resources.RequestByInstanceIdResource;
import org.folio.circulation.resources.RequestCirculationRulesEngineResource;
import org.folio.circulation.resources.RequestCollectionResource;
import org.folio.circulation.resources.RequestHoldShelfClearanceResource;
import org.folio.circulation.resources.RequestQueueResource;
import org.folio.circulation.resources.RequestScheduledNoticeProcessingResource;
import org.folio.circulation.resources.ScheduledAnonymizationProcessingResource;
import org.folio.circulation.resources.TenantActivationResource;
import org.folio.circulation.resources.agedtolost.ScheduledAgeToLostFeeChargingResource;
import org.folio.circulation.resources.agedtolost.ScheduledAgeToLostResource;
import org.folio.circulation.resources.handlers.LoanRelatedFeeFineClosedHandlerResource;
import org.folio.circulation.resources.renewal.OverrideRenewalByBarcodeResource;
import org.folio.circulation.resources.renewal.RenewByBarcodeResource;
import org.folio.circulation.resources.renewal.RenewByIdResource;
import org.folio.circulation.support.logging.Logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class CirculationVerticle extends AbstractVerticle {
  private HttpServer server;

  @Override
  public void start(Promise<Void> startFuture) {
    Logging.initialiseFormat();

    final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    log.info("Starting circulation module");

    Router router = Router.router(vertx);

    // bump up the connection pool size from the default value of 5
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(100));

    this.server = vertx.createHttpServer();

    new TenantActivationResource().register(router);

    new CheckOutByBarcodeResource("/circulation/check-out-by-barcode",
      client, new RegularCheckOutStrategy()).register(router);
    new CheckOutByBarcodeResource("/circulation/override-check-out-by-barcode",
      client, new OverrideCheckOutStrategy()).register(router);
    new CheckInByBarcodeResource(client).register(router);

    new RenewByBarcodeResource(client).register(router);
    new RenewByIdResource(client).register(router);
    new OverrideRenewalByBarcodeResource(client).register(router);

    new LoanCollectionResource(client).register(router);
    new RequestCollectionResource(client).register(router);
    new RequestQueueResource(client).register(router);
    new RequestByInstanceIdResource(client).register(router);

    new RequestHoldShelfClearanceResource(
      "/circulation/requests-reports/hold-shelf-clearance/:servicePointId", client)
      .register(router);
    new ItemsInTransitResource("/inventory-reports/items-in-transit", client)
      .register(router);
    new ItemsLostRequiringActualCostResource("/inventory-reports/items-lost-requiring-actual-costs", client)
      .register(router);
    new PickSlipsResource("/circulation/pick-slips/:servicePointId", client)
      .register(router);

    new CirculationRulesResource("/circulation/rules", client)
      .register(router);
    new LoanCirculationRulesEngineResource(
      "/circulation/rules/loan-policy",
      "/circulation/rules/loan-policy-all", client)
      .register(router);
    new OverdueFineCirculationRulesEngineResource(
      "/circulation/rules/overdue-fine-policy",
      "/circulation/rules/overdue-fine-policy-all", client)
      .register(router);
    new LostItemCirculationRulesEngineResource(
      "/circulation/rules/lost-item-policy",
      "/circulation/rules/lost-item-policy-all", client)
      .register(router);
    new RequestCirculationRulesEngineResource(
      "/circulation/rules/request-policy",
      "/circulation/rules/request-policy-all", client)
      .register(router);
    new NoticeCirculationRulesEngineResource(
      "/circulation/rules/notice-policy",
      "/circulation/rules/notice-policy-all", client)
      .register(router);

    new DueDateScheduledNoticeProcessingResource(client).register(router);
    new DueDateNotRealTimeScheduledNoticeProcessingResource(client).register(router);
    new RequestScheduledNoticeProcessingResource(client).register(router);
    new FeeFineScheduledNoticeProcessingResource(client).register(router);
    new ExpiredSessionProcessingResource(client).register(router);
    new LoanAnonymizationResource(client).register(router);
    new DeclareLostResource(client).register(router);
    new ScheduledAnonymizationProcessingResource(client).register(router);
    new EndPatronActionSessionResource(client).register(router);
    new ClaimItemReturnedResource(client).register(router);
    new ChangeDueDateResource(client).register(router);
    new DeclareClaimedReturnedItemAsMissingResource(client).register(router);
    new ScheduledAgeToLostResource(client).register(router);
    new ScheduledAgeToLostFeeChargingResource(client).register(router);

    // Handlers
    new LoanRelatedFeeFineClosedHandlerResource(client).register(router);

    server.requestHandler(router)
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
  public void stop(Promise<Void> stopFuture) {
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
