package org.folio.circulation;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.folio.circulation.resources.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class CirculationVerticle extends AbstractVerticle {
  private HttpServer server;

  @Override
  public void start(Future<Void> startFuture) {
    Logging.initialiseFormat();

    final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    log.info("Starting circulation module");

    Router router = Router.router(vertx);

    HttpClient client = vertx.createHttpClient();

    this.server = vertx.createHttpServer();

    new CheckOutByBarcodeResource(client).register(router);
    new LoanCollectionResource(client).register(router);
    new RequestCollectionResource(client).register(router);

    new LoanRulesResource         ("/circulation/loan-rules", client)
      .register(router);
    new LoanRulesEngineResource   ("/circulation/loan-rules/apply",
                                   "/circulation/loan-rules/apply-all", client)
      .register(router);

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
