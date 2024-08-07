package org.folio.circulation.resources;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestLogEventJson;
import static org.folio.circulation.resources.context.RequestQueueType.FOR_INSTANCE;
import static org.folio.circulation.resources.context.RequestQueueType.FOR_ITEM;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.reorder.ReorderQueueRequest;
import org.folio.circulation.domain.representations.logs.LogEventType;
import org.folio.circulation.domain.validation.RequestQueueValidation;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.resources.context.RequestQueueType;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestQueueResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public static final String URI_BASE = "/circulation/requests/queue";
  public static final String INSTANCE_ID_PARAM_NAME = "instanceId";
  public static final String ITEM_ID_PARAM_NAME = "itemId";

  public RequestQueueResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration(format("%s/instance/:%s", URI_BASE, INSTANCE_ID_PARAM_NAME), router)
      .getMany(this::getQueueForInstance);
    new RouteRegistration(format("%s/instance/:%s/reorder", URI_BASE, INSTANCE_ID_PARAM_NAME), router)
      .create(this::reorderQueueForInstance);
    new RouteRegistration(format("%s/item/:%s", URI_BASE, ITEM_ID_PARAM_NAME), router)
      .getMany(this::getQueueForItem);
    new RouteRegistration(format("%s/item/:%s/reorder", URI_BASE, ITEM_ID_PARAM_NAME), router)
      .create(this::reorderQueueForItem);
  }

  private void getQueueForInstance(RoutingContext routingContext) {
    getQueue(routingContext, FOR_INSTANCE);
  }

  private void reorderQueueForInstance(RoutingContext routingContext) {
    reorderQueue(routingContext, FOR_INSTANCE);
  }

  private void getQueueForItem(RoutingContext routingContext) {
    getQueue(routingContext, FOR_ITEM);
  }

  private void reorderQueueForItem(RoutingContext routingContext) {
    reorderQueue(routingContext, FOR_ITEM);
  }

  private void getQueue(RoutingContext routingContext, RequestQueueType requestQueueType) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var requestQueueRepository = new RequestQueueRepository(
      RequestRepository.using(clients, itemRepository, userRepository, loanRepository));

    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    getRequestQueueByType(routingContext, requestQueueType, requestQueueRepository)
      .thenApply(r -> r.map(queue -> new MultipleRecords<>(queue.getRequests(), queue.size())))
      .thenApply(r -> r.map(requests ->
        requests.asJson(requestRepresentation::extendedRepresentation, "requests")))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private void reorderQueue(RoutingContext routingContext, RequestQueueType requestQueueType) {
    String idParamValue = getIdParameterValueByQueueType(routingContext, requestQueueType);

    ReorderRequestContext reorderContext = new ReorderRequestContext(requestQueueType, idParamValue,
      routingContext.getBodyAsJson().mapTo(ReorderQueueRequest.class));

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var requestRepository = RequestRepository.using(clients,
      itemRepository, userRepository, loanRepository);
    final var configurationRepository = new ConfigurationRepository(clients);
    final var settingsRepository = new SettingsRepository(clients);
    final var requestQueueRepository = new RequestQueueRepository(requestRepository);

    final UpdateRequestQueue updateRequestQueue = new UpdateRequestQueue(
      requestQueueRepository, requestRepository, new ServicePointRepository(clients),
      configurationRepository, RequestQueueService.using(clients), new CalendarRepository(clients));

    validateTlrFeatureStatus(settingsRepository, requestQueueType, idParamValue)
      .thenCompose(r -> r.after(tlrSettings ->
        getRequestQueueByType(routingContext, requestQueueType, requestQueueRepository)))
      .thenApply(r -> r.map(reorderContext::withRequestQueue))
      // Validation block
      .thenApply(RequestQueueValidation::queueIsFound)
      .thenApply(RequestQueueValidation::positionsAreSequential)
      .thenApply(RequestQueueValidation::queueIsConsistent)
      .thenApply(RequestQueueValidation::pageRequestsPositioning)
      .thenApply(RequestQueueValidation::fulfillingRequestsPositioning)
      // Business logic block
      .thenCompose(updateRequestQueue::onReorder)
      .thenApply(q -> publishReorderedQueue(eventPublisher, q))
      .thenCompose(r -> r.after(this::toRepresentation))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<TlrSettingsConfiguration>> validateTlrFeatureStatus(
    SettingsRepository settingsRepository, RequestQueueType requestQueueType,
    String idParamValue) {

    return settingsRepository.lookupTlrSettings()
      .thenApply(r -> r.failWhen(
        tlrSettings -> succeeded(
          requestQueueType == FOR_INSTANCE ^ tlrSettings.isTitleLevelRequestsFeatureEnabled()),
        tlrSettings -> singleValidationError(
          format("Refuse to reorder request queue, TLR feature status is %s.",
            tlrSettings.isTitleLevelRequestsFeatureEnabled() ? "ENABLED" : "DISABLED"),
          getIdParameterNameByQueueType(requestQueueType), idParamValue)
      ));
  }

  private Result<ReorderRequestContext> publishReorderedQueue(EventPublisher eventPublisher,
    Result<ReorderRequestContext> reorderRequestContext) {

    reorderRequestContext.after(r -> {
      CompletableFuture.runAsync(() -> {
        List<Request> reordered = r.getReorderRequestToRequestMap().values().stream().filter(Request::hasChangedPosition).collect(Collectors.toList());
        eventPublisher.publishLogRecord(mapToRequestLogEventJson(reordered), LogEventType.REQUEST_REORDERED);
      });
      return null;
    });
    return reorderRequestContext;
  }

  private CompletableFuture<Result<JsonObject>> toRepresentation(ReorderRequestContext context) {
    log.debug("toRepresentation:: parameters context: {}", () -> context);
    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    return completedFuture(succeeded(context.getRequestQueue()))
      .thenApply(r -> r.map(queue -> new MultipleRecords<>(queue.getRequests(), queue.size())))
      .thenApply(r -> r.map(requests -> requests
        .asJson(requestRepresentation::extendedRepresentation, "requests")));
  }

  /**
   * @param requestQueueType Request queue type - is it for instance or for item
   * @return Either instanceId or itemId parameter name depending on the queue type
   */
  private String getIdParameterNameByQueueType(RequestQueueType requestQueueType) {
    log.debug("getIdParameterNameByQueueType:: parameters requestQueueType: {}",
      () -> requestQueueType);
    if (requestQueueType == FOR_INSTANCE) {
      return INSTANCE_ID_PARAM_NAME;
    } else {
      return ITEM_ID_PARAM_NAME;
    }
  }

  /**
   *
   * @param routingContext Routing context
   * @param requestQueueType Request queue type - is it for instance or for item
   * @return Either instanceId or itemId parameter value depending on the queue type
   */
  private String getIdParameterValueByQueueType(RoutingContext routingContext,
    RequestQueueType requestQueueType) {

    return routingContext.request().getParam(getIdParameterNameByQueueType(requestQueueType));
  }

  private CompletableFuture<Result<RequestQueue>> getRequestQueueByType(
    RoutingContext routingContext, RequestQueueType requestQueueType,
    RequestQueueRepository requestQueueRepository) {

    String idParamValue = getIdParameterValueByQueueType(routingContext, requestQueueType);
    log.info("getRequestQueueByType:: requestQueueType: {}", requestQueueType);
    if (requestQueueType == FOR_INSTANCE) {
      return requestQueueRepository.getByInstanceId(idParamValue);
    } else {
      return requestQueueRepository.getByItemId(idParamValue);
    }
  }
}
