package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CirculationSetting;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

public class CirculationSettingsRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public static final String RECORDS_PROPERTY_NAME = "circulationSettings";

  private final CollectionResourceClient circulationSettingsStorageClient;

  public CirculationSettingsRepository(Clients clients) {
    circulationSettingsStorageClient = clients.circulationSettingsStorageClient();
  }

  public CompletableFuture<Result<CirculationSetting>> getById(String id) {
    log.debug("getById:: parameters id: {}", id);

    return FetchSingleRecord.<CirculationSetting>forRecord(RECORDS_PROPERTY_NAME)
      .using(circulationSettingsStorageClient)
      .mapTo(CirculationSetting::from)
      .whenNotFound(failed(new RecordNotFoundFailure(RECORDS_PROPERTY_NAME, id)))
      .fetch(id);
  }

  public CompletableFuture<Result<Optional<CirculationSetting>>> findByName(String name) {
    return findByNames(List.of(name))
      .thenApply(mapResult(settings -> settings.stream().findFirst()));
  }

  public CompletableFuture<Result<Collection<CirculationSetting>>> findByNames(Collection<String> names) {
    log.debug("findByNames:: names: {}", names);

    return CqlQuery.exactMatchAny("name", names)
      .after(query -> circulationSettingsStorageClient.getMany(query, limit(names.size())))
      .thenApply(flatMapResult(r -> MultipleRecords.from(r, CirculationSetting::from, RECORDS_PROPERTY_NAME)
        .map(MultipleRecords::getRecords)));
  }

  public CompletableFuture<Result<MultipleRecords<CirculationSetting>>> findBy(String query) {
    return circulationSettingsStorageClient.getManyWithRawQueryStringParameters(query)
      .thenApply(flatMapResult(response ->
        MultipleRecords.from(response, CirculationSetting::from, RECORDS_PROPERTY_NAME)));
  }

  public CompletableFuture<Result<CirculationSetting>> create(
    CirculationSetting circulationSetting) {

    log.debug("create:: parameters circulationSetting: {}", circulationSetting);

    final var storageCirculationSetting = circulationSetting.getRepresentation();

    return circulationSettingsStorageClient.post(storageCirculationSetting)
      .thenApply(interpreter()::flatMap);
  }

  public CompletableFuture<Result<CirculationSetting>> update(
    CirculationSetting circulationSetting) {

    log.debug("update:: parameters circulationSetting: {}", circulationSetting);

    final var storageCirculationSetting = circulationSetting.getRepresentation();

    return circulationSettingsStorageClient.put(circulationSetting.getId(), storageCirculationSetting)
      .thenApply(interpreter()::flatMap);
  }

  private ResponseInterpreter<CirculationSetting> interpreter() {
    return new ResponseInterpreter<CirculationSetting>()
      .flatMapOn(201, mapUsingJson(CirculationSetting::from))
      .otherwise(forwardOnFailure());
  }
}
