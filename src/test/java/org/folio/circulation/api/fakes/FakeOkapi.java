package org.folio.circulation.api.fakes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.folio.circulation.api.APITestSuite;

public class FakeOkapi extends AbstractVerticle {

  private static final int PORT_TO_USE = 9493;
  private static final String address =
    String.format("http://localhost:%s", PORT_TO_USE);

  private HttpServer server;

  public static String getAddress() {
    return address;
  }

  public void start(Future<Void> startFuture) {
    System.out.println("Starting fake loan storage module");

    Router router = Router.router(vertx);

    this.server = vertx.createHttpServer();

    RegisterFakeLoansStorageModule(router);
    registerFakeItemsModule(router);
    registerFakeMaterialTypesModule(router);

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

  private void registerFakeMaterialTypesModule(Router router) {
    registerFakeModule(router, "/material-type", "mtypes");
  }

  private void registerFakeItemsModule(Router router) {
    registerFakeModule(router, "/item-storage/items", "items");
  }

  private void RegisterFakeLoansStorageModule(Router router) {
    registerFakeModule(router, "/loan-storage/loans", "loans");
  }

  private void registerFakeModule(
    Router router,
    String rootPath,
    String collectionPropertyName) {

    new FakeStorageModule(rootPath, collectionPropertyName,
      APITestSuite.TENANT_ID).register(router);
  }
}
