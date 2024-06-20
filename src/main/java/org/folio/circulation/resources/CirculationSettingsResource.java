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
    log.debug("replace:: Creating circulation setting: {}", circulationSetting);

    ofAsync(circulationSetting)
      .thenApply(refuseWhenCirculationSettingIsInvalid())
      .thenCompose(r -> r.after(circulationSettingsRepository::create))
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
    log.debug("replace:: Replacing circulation setting : {}", circulationSetting);

    ofAsync(circulationSetting)
      .thenApply(refuseWhenCirculationSettingIsInvalid())
      .thenCompose(r -> r.after(circulationSettingsRepository::update))
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
      .thenApply(r -> r.map(providedId -> UUID.fromString(providedId).toString()))
      .thenCompose(r -> r.after(circulationSettingsRepository::getById))
      .thenApply(r -> r.map(CirculationSetting::getRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void delete(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    ofAsync(routingContext.request().getParam("id"))
      .thenApply(refuseWhenIdIsInvalid())
      .thenApply(r -> r.map(providedId -> UUID.fromString(providedId).toString()))
      .thenCompose(r -> r.after(clients.circulationSettingsStorageClient()::delete))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void getMany(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var circulationSettingsRepository = new CirculationSettingsRepository(clients);

    final var query = routingContext.request().query();

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

  private void setRandomIdIfMissing(JsonObject representation) {
    final var providedId = getProperty(representation, "id");
    if (providedId == null) {
      representation.put("id", UUID.randomUUID().toString());
    }
  }

  private Function<Result<CirculationSetting>, Result<CirculationSetting>>
  refuseWhenCirculationSettingIsInvalid() {

    return r -> r.failWhen(circulationSetting -> succeeded(circulationSetting == null),
      circulationSetting -> singleValidationError("Circulation setting JSON is malformed", "", ""));
  }

  private Function<Result<String>, Result<String>> refuseWhenIdIsInvalid() {
    return r -> r.failWhen(id -> succeeded(!uuidIsValid(id)),
      circulationSetting -> singleValidationError("Circulation setting ID is not a valid UUID",
        "", ""));
  }

  private boolean uuidIsValid(String providedId) {
    try {
      return providedId != null && providedId.equals(UUID.fromString(providedId).toString());
    } catch(IllegalArgumentException e) {
      log.debug("refuseWhenIdIsInvalid:: Invalid UUID");
      return false;
    }
  }
}
