package org.folio.circulation.resources;

import static org.folio.circulation.infrastructure.storage.CirculationSettingsRepository.RECORDS_PROPERTY_NAME;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.UUID;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CirculationSetting;
import org.folio.circulation.infrastructure.storage.CirculationSettingsRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizationDueDateStorageRepository;
import org.folio.circulation.services.CirculationSettingsService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class CirculationSettingsResource extends CollectionResource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public CirculationSettingsResource(HttpClient client) {
    super(client, "/circulation/settings");
  }

  @Override
  void create(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var circulationSettingsRepository = new CirculationSettingsRepository(clients);

    final var incomingRepresentation = routingContext.body().asJsonObject();
    setRandomIdIfMissing(incomingRepresentation);
    final var circulationSetting = CirculationSetting.from(incomingRepresentation);
    log.debug("create:: Creating circulation setting: {}", () -> circulationSetting);

    ofAsync(circulationSetting)
      .thenApply(refuseWhenCirculationSettingIsInvalid())
      .thenCompose(r -> r.after(circulationSettingsRepository::create))
      .thenApply(r -> r.peek(setting -> invalidateAnonymizationDueDatesIfLoanHistory(clients, setting)))
      .thenApply(r -> r.map(CirculationSetting::getRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::created))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void replace(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var circulationSettingsRepository = new CirculationSettingsRepository(clients);

    final var incomingRepresentation = routingContext.body().asJsonObject();
    final var circulationSetting = CirculationSetting.from(incomingRepresentation);
    log.debug("replace:: Replacing circulation setting : {}", () -> circulationSetting);

    ofAsync(circulationSetting)
      .thenApply(refuseWhenCirculationSettingIsInvalid())
      .thenCompose(r -> r.after(circulationSettingsRepository::update))
      .thenApply(r -> r.peek(setting -> invalidateAnonymizationDueDatesIfLoanHistory(clients, setting)))
      .thenApply(r -> r.map(CirculationSetting::getRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::created))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void get(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var circulationSettingsRepository = new CirculationSettingsRepository(clients);

    ofAsync(routingContext.request().getParam("id"))
      .thenApply(refuseWhenIdIsInvalid())
      .thenApply(r -> r.peek(id -> log.debug("get:: parameters id: {}", id)))
      .thenCompose(r -> r.after(circulationSettingsRepository::getById))
      .thenApply(r -> r.map(CirculationSetting::getRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void delete(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var circulationSettingsRepository = new CirculationSettingsRepository(clients);

    ofAsync(routingContext.request().getParam("id"))
      .thenApply(refuseWhenIdIsInvalid())
      .thenApply(r -> r.peek(id -> log.debug("delete:: parameters id: {}", id)))
      // Fetch before deleting so we know whether the removed setting was
      // loan_history (a policy change that invalidates the due-dates).
      .thenCompose(r -> r.after(id -> circulationSettingsRepository.getById(id)
        .thenCompose(settingResult -> clients.circulationSettingsStorageClient().delete(id)
          .thenApply(deleteResult -> deleteResult.map(response -> {
            settingResult.peek(setting ->
              invalidateAnonymizationDueDatesIfLoanHistory(clients, setting));
            return response;
          })))))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void getMany(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var circulationSettingsRepository = new CirculationSettingsRepository(clients);

    final var query = routingContext.request().query();
    log.debug("get:: parameters id: {}", () -> query);

    circulationSettingsRepository.findBy(query)
      .thenApply(multipleLoanRecordsResult -> multipleLoanRecordsResult.map(multipleRecords ->
        multipleRecords.asJson(CirculationSetting::getRepresentation, RECORDS_PROPERTY_NAME)))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.loansStorage().delete()
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  /**
   * Any create/replace/delete of the loan_history setting invalidates the
   * derived due-dates, so clear them all and let the next sweep re-evaluate.
   * Fire-and-forget: a missed clear only delays re-evaluation.
   */
  private static void invalidateAnonymizationDueDatesIfLoanHistory(
    Clients clients, CirculationSetting setting) {

    if (setting == null
      || !CirculationSettingsService.SETTING_NAME_LOAN_HISTORY.equals(setting.getName())) {
      return;
    }
    log.info("invalidate:: loan_history changed, clearing all anonymization due-dates");
    new AnonymizationDueDateStorageRepository(clients).clearAll()
      .thenAccept(r -> r.applySideEffect(
        cleared -> log.info("invalidate:: cleared {} anonymization due-dates", cleared),
        failure -> log.warn("invalidate:: clearing stamps failed: {}", failure)));
  }

  private static void setRandomIdIfMissing(JsonObject representation) {
    final var providedId = getProperty(representation, "id");
    if (providedId == null) {
      representation.put("id", UUID.randomUUID().toString());
    }
  }

  private static Function<Result<CirculationSetting>, Result<CirculationSetting>>
  refuseWhenCirculationSettingIsInvalid() {

    return r -> r.failWhen(circulationSetting -> succeeded(circulationSetting == null),
      circulationSetting -> singleValidationError("Circulation setting JSON is invalid", "", ""));
  }

  private static Function<Result<String>, Result<String>> refuseWhenIdIsInvalid() {
    return r -> r.failWhen(id -> succeeded(!uuidIsValid(id)),
      circulationSetting -> singleValidationError("Circulation setting ID is not a valid UUID",
        "", ""));
  }

  private static boolean uuidIsValid(String providedId) {
    try {
      return providedId != null && providedId.equals(UUID.fromString(providedId).toString());
    } catch(IllegalArgumentException e) {
      log.warn("uuidIsValid:: Invalid UUID");
      return false;
    }
  }
}
