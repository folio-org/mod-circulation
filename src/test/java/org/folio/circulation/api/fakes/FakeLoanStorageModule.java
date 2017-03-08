package org.folio.circulation.api.fakes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.http.server.JsonResponse;

import java.util.HashMap;
import java.util.Map;

public class FakeLoanStorageModule extends AbstractVerticle {

  private static final int PORT_TO_USE = 9493;
  private static final String address =
    String.format("http://localhost:%s", PORT_TO_USE);

  private static final String rootPath = "/loan-storage/loans";

  private HttpServer server;

  private final Map<String, Map<String, JsonObject>> storedLoansByTenant;

  public static String getAddress() {
    return address;
  }

  public FakeLoanStorageModule() {
    storedLoansByTenant = new HashMap<>();
  }

  public void start(Future<Void> startFuture) {
    System.out.println("Starting Fake loan storage module");

    Router router = Router.router(vertx);

    this.server = vertx.createHttpServer();

    JsonObject config = vertx.getOrCreateContext().config();

    router.post(rootPath + "*").handler(BodyHandler.create());
    router.post(rootPath).handler(this::create);

    server.requestHandler(router::accept)
      .listen(PORT_TO_USE, result -> {
        if (result.succeeded()) {
          System.out.println(
            String.format("Listening on %s", server.actualPort()));
          startFuture.complete();
        } else {
          startFuture.fail(result.cause());
        }
      });
  }

  private void create(RoutingContext routingContext) {
    JsonResponse.created(routingContext.response(),
      routingContext.getBodyAsJson());
  }

  public void stop(Future<Void> stopFuture) {
    System.out.println("Stopping inventory module");

    if(server != null) {
      server.close(result -> {
        if (result.succeeded()) {
          System.out.println(
            String.format("Stopped listening on %s", server.actualPort()));
          stopFuture.complete();
        } else {
          stopFuture.fail(result.cause());
        }
      });
    }
  }
}
