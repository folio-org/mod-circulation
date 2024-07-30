package org.folio.circulation.infrastructure.storage;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.PrintEventDetail;
import org.folio.circulation.domain.PrintEventRequest;
import org.folio.circulation.domain.Request;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

public class PrintEventsRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient printEventsStorageClient;
  private final CollectionResourceClient printEventsStorageStatusClient;
  private final CirculationSettingsRepository circulationSettingsRepository;

  public PrintEventsRepository(Clients clients) {
    this.printEventsStorageClient = clients.printEventsStorageClient();
    this.printEventsStorageStatusClient = clients.printEventsStorageStatusClient();
    this.circulationSettingsRepository = new CirculationSettingsRepository(clients);
  }

  public CompletableFuture<Result<Void>> create(PrintEventRequest printEventRequest) {
    log.info("create:: parameters printEvent: {}", printEventRequest);
    final var storagePrintEventRequest = printEventRequest.getRepresentation();
    final ResponseInterpreter<Void> interpreter = new ResponseInterpreter<Void>()
      .on(201, succeeded(null))
      .otherwise(forwardOnFailure());
    return printEventsStorageClient.post(storagePrintEventRequest).thenApply(interpreter::flatMap);
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findPrintEventDetails(
    MultipleRecords<Request> multipleRequests) {
    log.debug("findPrintEventDetails:: parameters multipleRequests: {}",
      () -> multipleRecordsAsString(multipleRequests));
    return validatePrintEventFeatureFlag()
      .thenCompose(isEnabled -> Boolean.TRUE.equals(isEnabled) ?
        fetchAndMapPrintEventDetails(multipleRequests)
        : completedFuture(succeeded(multipleRequests)));
  }

  private CompletableFuture<Result<MultipleRecords<Request>>> fetchAndMapPrintEventDetails(
    MultipleRecords<Request> multipleRequests) {
    log.debug("fetchAndMapPrintEventDetails:: parameters multipleRequests: {}",
      () -> multipleRecordsAsString(multipleRequests));
    var requestIds = multipleRequests.toKeys(Request::getId);
    if (requestIds.isEmpty()) {
      return completedFuture(succeeded(multipleRequests));
    }
    return fetchPrintDetailsByRequestIds(requestIds)
      .thenApply(printEventRecordsResult -> printEventRecordsResult
        .next(printEventRecords -> mapPrintEventDetailsToRequest(printEventRecords, multipleRequests)));
  }

  private CompletableFuture<Boolean> validatePrintEventFeatureFlag() {
    log.debug("validatePrintEventFeatureFlag:: Fetching and validating enablePrintLog flag from settings");
    return circulationSettingsRepository.findBy("query=name=printEventLogFeature")
      .thenApply(res -> Optional.ofNullable(res.value())
        .map(records -> records.getRecords().stream()
          .map(setting -> Boolean.valueOf(getProperty(setting.getValue(), "enablePrintLog")))
          .findFirst()
          .orElse(false))
        .orElse(false));
  }

  private CompletableFuture<Result<MultipleRecords<PrintEventDetail>>> fetchPrintDetailsByRequestIds
    (Collection<String> requestIds) {
    log.debug("fetchPrintDetailsByRequestIds:: fetching print event details for requestIds {}", requestIds);
    return printEventsStorageStatusClient.post(new JsonObject().put("requestIds", requestIds))
      .thenApply(flatMapResult(response ->
        MultipleRecords.from(response, PrintEventDetail::from, "printEventsStatusResponses")));
  }

  private Result<MultipleRecords<Request>> mapPrintEventDetailsToRequest(
    MultipleRecords<PrintEventDetail> printEventDetails, MultipleRecords<Request> requests) {
    log.debug("mapPrintEventDetailsToRequest:: Mapping print event details {} with requests {}",
      () -> multipleRecordsAsString(printEventDetails), () -> multipleRecordsAsString(requests));
    Map<String, PrintEventDetail> printEventDetailMap = printEventDetails.toMap(PrintEventDetail::getRequestId);
    return of(() ->
      requests.mapRecords(request -> request
        .withPrintEventDetail(printEventDetailMap.getOrDefault(request.getId(), null))));
  }

}
