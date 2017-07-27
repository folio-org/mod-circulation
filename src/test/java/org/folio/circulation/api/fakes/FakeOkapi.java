package org.folio.circulation.api.fakes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

import org.folio.circulation.api.APITestSuite;

import java.util.Arrays;

public class FakeOkapi extends AbstractVerticle {

  private static final int PORT_TO_USE = 9493;
  private static final String address =
    String.format("http://localhost:%s", PORT_TO_USE);

  private HttpServer server;
  private String loanRules = "";

  public static String getAddress() {
    return address;
  }

  @Override
  public void start(Future<Void> startFuture) {
    System.out.println("Starting fake loan storage module");

    Router router = Router.router(vertx);

    this.server = vertx.createHttpServer();

    register(router, "/material-types", "mtypes");
    register(router, "/loan-types", "loantypes");
    register(router, "/item-storage/items", "items");
    register(router, "/loan-storage/loans", "loans",
        "userId", "itemId", "loanDate", "action");
    registerLoanRulesStorage(router);

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

  @Override
  public void stop(Future<Void> stopFuture) {
    System.out.println("Stopping fake loan storage module");

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

  private void register(Router router, String rootPath, String collectionPropertyName,
      String... requiredProperties) {
    new FakeStorageModule(rootPath, collectionPropertyName,
          APITestSuite.TENANT_ID, Arrays.asList(requiredProperties)).register(router);
  }

  private void registerLoanRulesStorage(Router router) {
    router.put("/loan-rules-storage").handler(routingContext -> {
      routingContext.request().bodyHandler(body -> {
        loanRules = body.toString();
        routingContext.response().setStatusCode(204).end();
      });
    });
    router.get("/loan-rules-storage").handler(routingContext -> {
      routingContext.response().setStatusCode(200).end(loanRules);
    });
  }
}
