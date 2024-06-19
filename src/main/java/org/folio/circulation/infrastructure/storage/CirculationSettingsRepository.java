package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CirculationSetting;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.RecordNotFoundFailure;
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

  public CompletableFuture<Result<MultipleRecords<CirculationSetting>>> findBy(String query) {
    log.debug("findBy:: parameters query: {}", query);

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
