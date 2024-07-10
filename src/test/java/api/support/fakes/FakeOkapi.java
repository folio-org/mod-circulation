package api.support.fakes;

import static api.support.APITestContext.circulationModulePort;
import static api.support.APITestContext.createWebClient;
import static api.support.fakes.Storage.getStorage;
import static api.support.fakes.StorageSchema.validatorForCheckInStorageSchema;
import static api.support.fakes.StorageSchema.validatorForLocationCampSchema;
import static api.support.fakes.StorageSchema.validatorForLocationInstSchema;
import static api.support.fakes.StorageSchema.validatorForLocationLibSchema;
import static api.support.fakes.StorageSchema.validatorForNoteSchema;
import static api.support.fakes.StorageSchema.validatorForStorageItemSchema;
import static api.support.fakes.StorageSchema.validatorForStorageLoanSchema;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.folio.circulation.support.http.server.ForwardResponse.forward;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.rest.tools.utils.NetworkUtils.nextFreePort;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Objects;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.results.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class FakeOkapi extends AbstractVerticle {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final int PORT_TO_USE = nextFreePort();
  private static final String address =
    String.format("http://localhost:%s", PORT_TO_USE);

  private HttpServer server;
  private String circulationRules = "{ \"rulesAsText\": \"\" }";

  public static String getAddress() {
    return address;
  }

  @Override
  public void start(Promise<Void> startFuture) throws IOException {
    log.debug("Starting fake loan storage module");

    Router router = Router.router(vertx);

    this.server = vertx.createHttpServer();

    forwardRequestsToApplyCirculationRulesBackToCirculationModule(router);

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
      .withRequiredProperties("name", "code", "source")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("contributor name type")
      .withRootPath("/contributor-name-types")
      .withCollectionPropertyName("contributorNameTypes")
      .withRequiredProperties("name")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("instance")
      .withRootPath("/instance-storage/instances")
      .withCollectionPropertyName("instances")
      .withRequiredProperties("source", "title", "instanceTypeId")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("holding")
      .withRootPath("/holdings-storage/holdings")
      .withCollectionPropertyName("holdingsRecords")
      .withRequiredProperties("instanceId", "permanentLocationId")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("item")
      .withRootPath("/item-storage/items")
      .withRecordPreProcessor(asList(
        StorageRecordPreProcessors::setEffectiveLocationIdForItem,
        StorageRecordPreProcessors::setItemStatusDateForItem,
        StorageRecordPreProcessors::setEffectiveCallNumberComponents))
      .validateRecordsWith(validatorForStorageItemSchema())
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("loan")
      .withRootPath("/loan-storage/loans")
      .validateRecordsWith(validatorForStorageLoanSchema())
      .withRecordPreProcessor(singletonList(LoanHistoryProcessor::persistLoanHistory))
      .withChangeMetadata()
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withCollectionPropertyName("loansHistory")
      .withRootPath("/loan-storage/loan-history")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("fixed due date schedules")
      .withRootPath("/fixed-due-date-schedule-storage/fixed-due-date-schedules")
      .withCollectionPropertyName("fixedDueDateSchedules")
      .withUniqueProperties("name")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("loan policy")
      .withRootPath("/loan-policy-storage/loan-policies")
      .withCollectionPropertyName("loanPolicies")
      .withRequiredProperties("name", "loanable", "renewable")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("accounts")
      .withRootPath("/accounts")
      .withCollectionPropertyName("accounts")
      .withChangeMetadata()
      .create().register(router);

    new FakeStorageModuleBuilder()
        .withRecordName("feefineactions")
        .withRootPath("/feefineactions")
        .withCollectionPropertyName("feefineactions")
        .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("request policy")
      .withRootPath("/request-policy-storage/request-policies")
      .withCollectionPropertyName("requestPolicies")
      .withRequiredProperties("name")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("notice policy")
      .withRootPath("/patron-notice-policy-storage/patron-notice-policies")
      .withCollectionPropertyName("patronNoticePolicies")
      .withRequiredProperties("name", "active")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("overdue fine policy")
      .withRootPath("/overdue-fines-policies")
      .withCollectionPropertyName("overdueFinePolicies")
      .withRequiredProperties("name")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("lost item fee policy")
      .withRootPath("/lost-item-fees-policies")
      .withCollectionPropertyName("lostItemFeePolicies")
      .withRequiredProperties("name")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("user group")
      .withRootPath("/groups")
      .withCollectionPropertyName("usergroups")
      .withRequiredProperties("group", "desc")
      .withUniqueProperties("group")
      .withChangeMetadata()
      .disallowCollectionDelete()
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("Address type")
      .withRootPath("/addresstypes")
      .withCollectionPropertyName("addressTypes")
      .withRequiredProperties("addressType")
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
      .withRequiredProperties("instanceId", "requesterId", "requestType",
        "requestDate", "fulfillmentPreference")
      .withDisallowedProperties("pickupServicePoint", "loan", "deliveryAddress")
      .withRecordConstraint(this::requestHasSamePosition)
      .withChangeMetadata()
      .withBatchUpdate("/request-storage-batch/requests")
      .withBatchUpdatePreProcessor(this::resetPositionsBeforeBatchUpdate)
      .create().register(router);

    registerCirculationRulesStorage(router);
    FakeCalendarOkapi.registerCalendarAllDates(router);
    FakeCalendarOkapi.registerCalendarSurroundingDates(router);
    registerFakeStorageLoansAnonymize(router);

    new FakeStorageModuleBuilder()
      .withRecordName("institution")
      .withRootPath("/location-units/institutions")
      .withCollectionPropertyName("locinsts")
      .validateRecordsWith(validatorForLocationInstSchema())
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("campus")
      .withRootPath("/location-units/campuses")
      .withCollectionPropertyName("loccamps")
      .validateRecordsWith(validatorForLocationCampSchema())
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("library")
      .withRootPath("/location-units/libraries")
      .withCollectionPropertyName("loclibs")
      .validateRecordsWith(validatorForLocationLibSchema())
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("locations")
      .withRootPath("/locations")
      .withCollectionPropertyName("locations")
      .withRequiredProperties("name", "code", "institutionId", "campusId",
        "libraryId", "primaryServicePoint")
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("cancellationReason")
      .withCollectionPropertyName("cancellationReasons")
      .withRootPath("/cancellation-reason-storage/cancellation-reasons")
      .withRequiredProperties("name", "description")
      .withChangeMetadata()
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("service point")
      .withCollectionPropertyName("servicepoints")
      .withRootPath("/service-points")
      .withRequiredProperties("name", "code", "discoveryDisplayName")
      .withUniqueProperties("name")
      .withChangeMetadata()
      .disallowCollectionDelete()
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("configuration")
      .withCollectionPropertyName("configs")
      .withRootPath("/configurations/entries")
      .withChangeMetadata()
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withCollectionPropertyName("expiredSessions")
      .withRootPath("/patron-action-session-storage/expired-session-patron-ids")
      .withQueryParameters("action_type", "session_inactivity_time")
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withCollectionPropertyName("manualblocks")
      .withRootPath("/manualblocks")
      .withQueryParameters("userId")
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("scheduled notice")
      .withCollectionPropertyName("scheduledNotices")
      .withRootPath("/scheduled-notice-storage/scheduled-notices")
      .allowDeleteByQuery()
      .create()
      .register(router);


    new FakeStorageModuleBuilder()
      .withRecordName("patron action session")
      .withCollectionPropertyName("patronActionSessions")
      .withRootPath("/patron-action-session-storage/patron-action-sessions")
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("template")
      .withRootPath("/templates")
      .withCollectionPropertyName("templates")
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("identifierTypes")
      .withRootPath("/identifier-types")
      .withCollectionPropertyName("identifierTypes")
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("checkIns")
      .withRootPath("/check-in-storage/check-ins")
      .withCollectionPropertyName("checkIns")
      .validateRecordsWith(validatorForCheckInStorageSchema())
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withCollectionPropertyName("owners")
      .withRootPath("/owners")
      .create()
      .register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("feefines")
      .withRootPath("/feefines")
      .withCollectionPropertyName("feefines")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("note")
      .withRootPath("/notes")
      .withCollectionPropertyName("notes")
      .withChangeMetadata()
      .validateRecordsWith(validatorForNoteSchema())
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("noteType")
      .withRootPath("/note-types")
      .withCollectionPropertyName("noteTypes")
      .withQueryParameters("name")
      .create().register(router);

    router.delete("/_/tenant").handler(this::removeAllData);

    FakePubSub.register(router);
    FakeModNotify.register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("automatedPatronBlock")
      .withRootPath("/automated-patron-blocks")
      .withCollectionPropertyName("automatedPatronBlocks")
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("actualCostRecord")
      .withRootPath("/actual-cost-record-storage/actual-cost-records")
      .withCollectionPropertyName("actualCostRecords")
      .withChangeMetadata()
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("departments")
      .withRootPath("/departments")
      .withCollectionPropertyName("departments")
      .withChangeMetadata()
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("check-out-lock-storage")
      .withRootPath("/check-out-lock-storage")
      .withCollectionPropertyName("check-out-lock-storage")
      .withChangeMetadata()
      .withRecordConstraint(this::userHasAlreadyAcquiredLock)
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("settings")
      .withRootPath("/settings/entries")
      .withCollectionPropertyName("items")
      .withChangeMetadata()
      .withRecordConstraint(this::userHasAlreadyAcquiredLock)
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRootPath("/circulation-item")
      .withCollectionPropertyName("items")
      .withChangeMetadata()
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRecordName("circulationSettings")
      .withCollectionPropertyName("circulationSettings")
      .withRootPath("/circulation-settings-storage/circulation-settings")
      .withChangeMetadata()
      .create().register(router);

    new FakeStorageModuleBuilder()
      .withRootPath("/print-events-storage/print-events-entry")
      .withChangeMetadata()
      .create().register(router);

    new FakeFeeFineOperationsModule().register(router);

    server.requestHandler(router)
      .listen(PORT_TO_USE, result -> {
        if (result.succeeded()) {
          log.info("Listening on {}", server.actualPort());
          startFuture.complete();
        } else {
          startFuture.fail(result.cause());
        }
      });
  }

  private Result<Object> userHasAlreadyAcquiredLock(Collection<JsonObject> existingRequests, JsonObject currentRequest) {
    return existingRequests.stream()
      .filter(req -> Objects.equals(req.getString("userId"),
        currentRequest.getString("userId")))
      .findAny()
      .map(r -> ValidationErrorFailure.failedValidation("Unable to acquire lock", "", ""))
      .orElse(Result.succeeded(null));
  }

  private Result<Object> requestHasSamePosition(
    Collection<JsonObject> existingRequests, JsonObject newOrUpdatedRequest) {

    try {
      return existingRequests.stream()
        .filter(request -> !Objects.equals(request.getString("id"),
          newOrUpdatedRequest.getString("id")))
        .filter(request -> Objects.equals(request.getString("itemId"),
          newOrUpdatedRequest.getString("itemId")))
        .filter(request -> newOrUpdatedRequest.getInteger("position") != null &&
          Objects.equals(request.getInteger("position"),
            newOrUpdatedRequest.getInteger("position")))
        .findAny()
        .map(r -> ValidationErrorFailure.failedValidation(
          "Cannot have more than one request with the same position in the queue",
          "itemId", r.getString("itemId")))
        .orElse(Result.succeeded(null));
    } catch (Exception e) {
      return failedDueToServerError(e);
    }
  }

  private void forwardRequestsToApplyCirculationRulesBackToCirculationModule(
    Router router) {
    //During loan creation, a request to /circulation/rules/loan-policy is made,
    //which is effectively to itself, so needs to be routed back
    router.get("/circulation/rules/loan-policy").handler(context -> {
      forwardApplyingCirculationRulesRequest(context, "loan-policy");
    });

    router.get("/circulation/rules/overdue-fine-policy").handler(context -> {
      forwardApplyingCirculationRulesRequest(context, "overdue-fine-policy");
    });

    router.get("/circulation/rules/lost-item-policy").handler(context -> {
      forwardApplyingCirculationRulesRequest(context, "lost-item-policy");
    });

    router.get("/circulation/rules/notice-policy").handler(context -> {
      forwardApplyingCirculationRulesRequest(context, "notice-policy");
    });

    router.get("/circulation/rules/request-policy").handler(context -> {
      forwardApplyingCirculationRulesRequest(context, "request-policy");
    });
  }

  private void forwardApplyingCirculationRulesRequest(RoutingContext context,
    String policyNamePartialPath) {

    OkapiHttpClient client = createWebClient();

    client.get(String.format("http://localhost:%s/circulation/rules/%s?%s",
      circulationModulePort(), policyNamePartialPath, context.request().query()))
      .thenAccept(result -> result.applySideEffect(
        response -> forward(context.response(), response),
        cause -> cause.writeTo(context.response())));
  }

  @Override
  public void stop(Promise<Void> stopFuture) {
    log.debug("Stopping fake okapi");

    if (server != null) {
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

  private void registerFakeStorageLoansAnonymize(Router router) {
    router.post("/anonymize-storage-loans")
      .handler(new FakeLoanAnonymizationResource());
  }

  private void registerCirculationRulesStorage(Router router) {
    router.put("/circulation-rules-storage").handler(routingContext -> {
      log.debug("/circulation-rules-storage PUT");
      routingContext.request().bodyHandler(body -> {
        circulationRules = body.toString();
        log.debug("/circulation-rules-storage PUT body={}", circulationRules);
        routingContext.response().setStatusCode(204).end();
      }).exceptionHandler(ex -> {
        log.error("Unhandled exception in body handler", ex);
        routingContext.response().setStatusCode(500).end(ExceptionUtils.getStackTrace(ex));
      });
    });
    router.get("/circulation-rules-storage").handler(routingContext -> {
      log.debug("/circulation-rules-storage GET returns {}", circulationRules);
      routingContext.response().setStatusCode(200).end(circulationRules);
    });
  }

  private JsonObject resetPositionsBeforeBatchUpdate(JsonObject batchUpdateRequest) {
    JsonArray requests = batchUpdateRequest.getJsonArray("requests");

    JsonArray requestsCopy = requests.copy();
    requestsCopy
      .forEach(requestCopy -> ((JsonObject) requestCopy).remove("position"));

    batchUpdateRequest.put("requests", requestsCopy.addAll(requests));
    return batchUpdateRequest;
  }

  private void removeAllData(RoutingContext routingContext) {
    getStorage().removeAll();

    noContent().writeTo(routingContext.response());
  }
}
