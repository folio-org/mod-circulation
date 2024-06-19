package org.folio.circulation.resources;

import static org.folio.circulation.infrastructure.storage.CirculationSettingsRepository.RECORDS_PROPERTY_NAME;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;

import java.lang.invoke.MethodHandles;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CirculationSetting;
import org.folio.circulation.infrastructure.storage.CirculationSettingsRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

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

    circulationSettingsRepository.create(circulationSetting)
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

    circulationSettingsRepository.update(circulationSetting)
      .thenApply(r -> r.map(CirculationSetting::getRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::created))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void get(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var circulationSettingsRepository = new CirculationSettingsRepository(clients);

    final var id = routingContext.request().getParam("id");
    log.debug("get:: Requested circulation setting ID: {}", id);

    circulationSettingsRepository.getById(id)
      .thenApply(r -> r.map(CirculationSetting::getRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void delete(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    String id = routingContext.request().getParam("id");
    log.debug("delete:: Deleting circulation setting ID: {}", id);

    clients.circulationSettingsStorageClient().delete(id)
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  @Override
  void getMany(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var circulationSettingsRepository = new CirculationSettingsRepository(clients);

    final var query = routingContext.request().query();
    log.debug("get:: Requested circulation settings by query: {}", query);

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
}
