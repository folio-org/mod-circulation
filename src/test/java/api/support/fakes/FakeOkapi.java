package api.support.fakes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.apache.commons.lang3.exception.ExceptionUtils;
import api.APITestSuite;
import org.folio.circulation.support.http.client.BufferHelper;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class FakeOkapi extends AbstractVerticle {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final int PORT_TO_USE = 9493;
  private static final String address =
    String.format("http://localhost:%s", PORT_TO_USE);

  private HttpServer server;
  private String loanRules = "{ \"loanRulesAsTextFile\": \"\" }";

  public static String getAddress() {
    return address;
  }

  @Override
  public void start(Future<Void> startFuture) {
    log.debug("Starting fake loan storage module");

    Router router = Router.router(vertx);

    this.server = vertx.createHttpServer();

    forwardRequestsToApplyLoanRulesBackToCirculationModule(router);

    new FakeStorageModuleBuilder()
      .withRecordName("material type")
      .withRootPath("/material-types")
      .withCollectionPropertyName("mtypes")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("loan type")
      .withRootPath("/loan-types")
      .withCollectionPropertyName("loantypes")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("instance type")
      .withRootPath("/instance-types")
      .withCollectionPropertyName("instanceTypes")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("contributor name type")
      .withRootPath("/contributor-name-types")
      .withCollectionPropertyName("contributorNameTypes")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("item")
      .withRootPath("/item-storage/items")
      .withRequiredProperties("holdingsRecordId", "materialTypeId", "permanentLoanTypeId")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("holding")
      .withRootPath("/holdings-storage/holdings")
      .withCollectionPropertyName("holdingsRecords")
      .withRequiredProperties("instanceId", "permanentLocationId")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("instance")
      .withRootPath("/instance-storage/instances")
      .withCollectionPropertyName("instances")
      .withRequiredProperties("source", "title", "instanceTypeId")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("loan")
      .withRootPath("/loan-storage/loans")
      .withRequiredProperties("userId", "itemId", "loanDate", "action")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("loan policy")
      .withRootPath("/loan-policy-storage/loan-policies")
      .withRequiredProperties("name", "loanable", "renewable")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("user group")
      .withRootPath("/groups")
      .withCollectionPropertyName("usergroups")
      .withRequiredProperties("group", "desc")
      .disallowCollectionDelete()
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("user")
      .withRootPath("/users")
      .withRequiredProperties("id", "username")
      .withUniqueProperties("username")
      .disallowCollectionDelete()
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("proxyFor")
      .withCollectionPropertyName("proxiesFor")
      .withRootPath("/proxiesfor")
      .withRequiredProperties("id")
      .withUniqueProperties("id")
      .disallowCollectionDelete()
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("request")
      .withRootPath("/request-storage/requests")
      .withRequiredProperties("itemId", "requesterId", "requestType",
        "requestDate", "fulfilmentPreference")
      .create().register(router);

    registerLoanRulesStorage(router);

    new FakeStorageModuleBuilder()
      .withRecordName("institution")
      .withRootPath("/location-units/institutions")
      .withCollectionPropertyName("locinsts")
      .withRequiredProperties("name")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("campus")
      .withRootPath("/location-units/campuses")
      .withCollectionPropertyName("loccamps")
      .withRequiredProperties("name", "institutionId")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("library")
      .withRootPath("/location-units/libraries")
      .withCollectionPropertyName("loclibs")
      .withRequiredProperties("name", "campusId")
      .create().register(router);
    
    new FakeStorageModuleBuilder()
      .withRecordName("locations")
      .withRootPath("/locations")
      .withCollectionPropertyName("locations")
      .withRequiredProperties(
        "name",
        "code",
        "institutionId",
        "campusId",
        "libraryId")
      .create()
      .register(router);

    server.requestHandler(router::accept)
      .listen(PORT_TO_USE, result -> {
        if (result.succeeded()) {
          log.info("Listening on {}", server.actualPort());
          startFuture.complete();
        } else {
          startFuture.fail(result.cause());
        }
      });
  }

  private void forwardRequestsToApplyLoanRulesBackToCirculationModule(Router router) {
    //During loan creation, a request to /circulation/loan-rules/apply is made,
    //which is effectively to itself, so needs to be routed back
    router.get("/circulation/loan-rules/apply").handler(context -> {
      OkapiHttpClient client = APITestSuite.createClient(throwable ->
        ServerErrorResponse.internalError(context.response(),
          String.format("Exception when forward loan rules apply request: %s",
            throwable.getMessage())));

      client.get(String.format("http://localhost:%s/circulation/loan-rules/apply?%s"
        , APITestSuite.circulationModulePort(), context.request().query()),
        httpClientResponse ->
          httpClientResponse.bodyHandler(buffer ->
            ForwardResponse.forward(context.response(), httpClientResponse,
              BufferHelper.stringFromBuffer(buffer))));
    });
  }

  @Override
  public void stop(Future<Void> stopFuture) {
    log.debug("Stopping fake okapi");

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

  private void registerLoanRulesStorage(Router router) {
    router.put("/loan-rules-storage").handler(routingContext -> {
      log.debug("/loan-rules-storage PUT");
      routingContext.request().bodyHandler(body -> {
        loanRules = body.toString();
        log.debug("/loan-rules-storage PUT body={}", loanRules);
        routingContext.response().setStatusCode(204).end();
      }).exceptionHandler(ex -> {
        log.error("Unhandled exception in body handler", ex);
        routingContext.response().setStatusCode(500).end(ExceptionUtils.getStackTrace(ex));
      });
    });
    router.get("/loan-rules-storage").handler(routingContext -> {
      log.debug("/loan-rules-storage GET returns {}", loanRules);
      routingContext.response().setStatusCode(200).end(loanRules);
    });
  }
}
