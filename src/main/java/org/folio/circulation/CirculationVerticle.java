package org.folio.circulation;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.resources.LoanCollectionResource;
import org.folio.circulation.resources.LoanRulesEngineResource;
import org.folio.circulation.resources.LoanRulesResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CirculationVerticle extends AbstractVerticle {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private HttpServer server;

  @Override
  public void start(Future<Void> startFuture) {
    log.info("Starting circulation module");

    Router router = Router.router(vertx);

    this.server = vertx.createHttpServer();

    new LoanCollectionResource ("/circulation/loans"     ).register(router);
    new LoanRulesResource      ("/circulation/loan-rules").register(router);
    new LoanRulesEngineResource("/circulation/loan-rules/apply",
                                "/circulation/loan-rules/apply-all").register(router);

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
