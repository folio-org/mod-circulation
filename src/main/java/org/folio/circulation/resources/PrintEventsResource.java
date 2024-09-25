package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CirculationSetting;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.PrintEventRequest;
import org.folio.circulation.infrastructure.storage.CirculationSettingsRepository;
import org.folio.circulation.infrastructure.storage.PrintEventsRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

public class PrintEventsResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String PRINT_EVENT_FLAG_QUERY = "query=name=printEventLogFeature";
  private static final String PRINT_EVENT_FEATURE_DISABLED_ERROR = "print event feature is disabled for this tenant";
  private static final String NO_CONFIG_FOUND_ERROR = "No configuration found for print event feature";
  private static final String MULTIPLE_CONFIGS_ERROR = "Multiple configurations found for print event feature";
  private static final String PRINT_EVENT_FLAG_PROPERTY_NAME = "enablePrintLog";

  public PrintEventsResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/print-events-entry", router)
      .create(this::create);
  }

  void create(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var printEventsRepository = new PrintEventsRepository(clients);
    final var circulationSettingsRepository = new CirculationSettingsRepository(clients);
    final var requestRepository = new RequestRepository(clients);
    final var incomingRepresentation = routingContext.body().asJsonObject();
    final var printEventRequest = PrintEventRequest.from(incomingRepresentation);

    log.info("create:: Creating print event: {}", printEventRequest);

    ofAsync(printEventRequest)
      .thenApply(refuseWhenPrintEventRequestIsInvalid())
      .thenCompose(r -> r.after(validatePrintEventFeatureFlag(circulationSettingsRepository)))
      .thenCompose(r -> r.after(validateRequests(requestRepository)))
      .thenCompose(r -> r.after(printEventsRepository::create))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private static Function<PrintEventRequest, CompletableFuture<Result<PrintEventRequest>>>
  validateRequests(RequestRepository requestRepository) {
    return printRequest -> requestRepository.fetchRequests(printRequest.getRequestIds())
      .thenApply(printRequestList -> printRequestList.map(Collection::size)).thenApply(size -> {
        if (size.value() != printRequest.getRequestIds().size()) {
          return Result.failed(singleValidationError("invalid request found", "", ""));
        }
        return succeeded(printRequest);
      });
  }

  private static Function<Result<PrintEventRequest>, Result<PrintEventRequest>>
  refuseWhenPrintEventRequestIsInvalid() {
    return r -> r.failWhen(printEventRequest -> succeeded(printEventRequest == null),
      circulationSetting -> singleValidationError("Print Event Request  JSON is invalid", "", ""));
  }

  private static Function<PrintEventRequest, CompletableFuture<Result<PrintEventRequest>>> validatePrintEventFeatureFlag(
    CirculationSettingsRepository circulationSettingsRepository) {
    return printEventRequest -> circulationSettingsRepository.findBy(PRINT_EVENT_FLAG_QUERY)
      .thenApply(result ->
        handleCirculationSettingResult(result.map(MultipleRecords::getRecords), printEventRequest)
      );
  }

  private static Result<PrintEventRequest> handleCirculationSettingResult(Result<Collection<CirculationSetting>> result,
                                                                          PrintEventRequest printEventRequest) {

    int size = result.value().size();
    if (size == 0) {
      return Result.failed(singleValidationError(NO_CONFIG_FOUND_ERROR, "", ""));
    } else if (size > 1) {
      return Result.failed(singleValidationError(MULTIPLE_CONFIGS_ERROR, "", ""));
    }
    boolean isEnabled = result.value().stream()
      .map(x -> Boolean.valueOf(getProperty(x.getValue(), PRINT_EVENT_FLAG_PROPERTY_NAME))).findFirst().orElse(true);

    if (!isEnabled) {
      return Result.failed(singleValidationError(PRINT_EVENT_FEATURE_DISABLED_ERROR, "", ""));
    }
    return succeeded(printEventRequest);
  }

}
