package org.folio.circulation.resources;

import static org.folio.circulation.domain.representations.RequestProperties.PROXY_USER_ID;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.results.AsynchronousResult.fromFutureResult;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.MappingFunctions.when;

import org.folio.circulation.domain.CreateRequestRepositories;
import org.folio.circulation.domain.CreateRequestService;
import org.folio.circulation.domain.MoveRequestProcessAdapter;
import org.folio.circulation.domain.MoveRequestService;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateLoan;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.UpdateRequestService;
import org.folio.circulation.domain.UpdateUponRequest;
import org.folio.circulation.domain.UserManualBlock;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeService;
import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.domain.validation.UserManualBlocksValidator;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.DeferFailureErrorHandler;
import org.folio.circulation.resources.handlers.error.FailFastErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestCollectionResource extends CollectionResource {
  public RequestCollectionResource(HttpClient client) {
    super(client, "/circulation/requests");
  }

  @Override
  public void register(Router router) {
    super.register(router);
    router.post("/circulation/requests/:id/move").handler(this::move);
  }

  @Override
  void create(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    final var representation = routingContext.getBodyAsJson();

    final var eventPublisher = new EventPublisher(routingContext);

    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients);
    final var loanPolicyRepository = new LoanPolicyRepository(clients);
    final var requestNoticeSender = RequestNoticeSender.using(clients);
    final var configurationRepository = new ConfigurationRepository(clients);
    final var userManualBlocksValidator = findWithCqlQuery(clients.userManualBlocksStorageClient(),
      "manualblocks", UserManualBlock::from);

    final var updateUponRequest = new UpdateUponRequest(new UpdateItem(clients),
      new UpdateLoan(clients, loanRepository, loanPolicyRepository),
      UpdateRequestQueue.using(clients));

    final var errorHandler = new DeferFailureErrorHandler(context.getHeaders());

    final var createRequestService = new CreateRequestService(
      new CreateRequestRepositories(RequestRepository.using(clients),
        new RequestPolicyRepository(clients), configurationRepository,
        new AutomatedPatronBlocksRepository(clients)),
      updateUponRequest, new RequestLoanValidator(loanRepository),
      requestNoticeSender, new UserManualBlocksValidator(userManualBlocksValidator),
      eventPublisher, errorHandler);

    final var requestFromRepresentationService = new RequestFromRepresentationService(
      new ItemRepository(clients, true, true, true),
      RequestQueueRepository.using(clients), userRepository, loanRepository,
      new ServicePointRepository(clients),
      createProxyRelationshipValidator(representation, clients),
      new ServicePointPickupLocationValidator(), errorHandler);

    final var scheduledNoticeService = RequestScheduledNoticeService.using(clients);

    fromFutureResult(requestFromRepresentationService.getRequestFrom(representation))
      .flatMapFuture(createRequestService::createRequest)
      .onSuccess(scheduledNoticeService::scheduleRequestNotices)
      .onSuccess(records -> eventPublisher.publishDueDateChangedEvent(records, clients))
      .map(RequestAndRelatedRecords::getRequest)
      .map(new RequestRepresentation()::extendedRepresentation)
      .map(JsonHttpResponse::created)
      .onComplete(context::write, context::write);
  }

  @Override
  void replace(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    final var representation = routingContext.getBodyAsJson();

    write(representation, "id", getRequestId(routingContext));

    final var requestRepository = RequestRepository.using(clients);
    final var updateRequestQueue = UpdateRequestQueue.using(clients);
    final var loanRepository = new LoanRepository(clients);
    final var loanPolicyRepository = new LoanPolicyRepository(clients);
    final var eventPublisher = new EventPublisher(routingContext);
    final var requestNoticeSender = RequestNoticeSender.using(clients);
    final var configurationRepository = new ConfigurationRepository(clients);
    final var userManualBlocksValidator = findWithCqlQuery(clients.userManualBlocksStorageClient(),
      "manualblocks", UserManualBlock::from);

    final var updateItem = new UpdateItem(clients);

    final var updateUponRequest = new UpdateUponRequest(updateItem,
      new UpdateLoan(clients, loanRepository, loanPolicyRepository), updateRequestQueue);

    final var errorHandler = new FailFastErrorHandler();

    final var createRequestService = new CreateRequestService(
      new CreateRequestRepositories(requestRepository,
        new RequestPolicyRepository(clients), configurationRepository,
        new AutomatedPatronBlocksRepository(clients)),
      updateUponRequest, new RequestLoanValidator(loanRepository),
      requestNoticeSender, new UserManualBlocksValidator(userManualBlocksValidator),
      eventPublisher, errorHandler);

    final var updateRequestService = new UpdateRequestService(requestRepository,
      updateRequestQueue, new ClosedRequestValidator(requestRepository),
      requestNoticeSender, updateItem, eventPublisher);

    final var requestFromRepresentationService = new RequestFromRepresentationService(
      new ItemRepository(clients, true, true, true),
      RequestQueueRepository.using(clients), new UserRepository(clients),
      loanRepository, new ServicePointRepository(clients),
      createProxyRelationshipValidator(representation, clients),
      new ServicePointPickupLocationValidator(), errorHandler);

    final var requestScheduledNoticeService = RequestScheduledNoticeService.using(clients);

    fromFutureResult(requestFromRepresentationService.getRequestFrom(representation))
      .flatMapFuture(when(requestRepository::exists, updateRequestService::replaceRequest,
        createRequestService::createRequest))
      .flatMapFuture(records -> eventPublisher.publishDueDateChangedEvent(records, clients))
      .map(requestScheduledNoticeService::rescheduleRequestNotices)
      .map(toFixedValue(NoContentResponse::noContent))
      .onComplete(context::write, context::write);
  }

  @Override
  void get(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    final var requestRepository = RequestRepository.using(clients);

    final var id = getRequestId(routingContext);

    fromFutureResult(requestRepository.getById(id))
      .map(new RequestRepresentation()::extendedRepresentation)
      .map(JsonHttpResponse::ok)
      .onComplete(context::write, context::write);
  }

  @Override
  void delete(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    final var id = getRequestId(routingContext);

    final var requestRepository = RequestRepository.using(clients);

    final var updateRequestQueue = new UpdateRequestQueue(RequestQueueRepository.using(clients),
      requestRepository, new ServicePointRepository(clients), new ConfigurationRepository(clients));

    fromFutureResult(requestRepository.getById(id))
      .flatMapFuture(requestRepository::delete)
      .flatMapFuture(updateRequestQueue::onDeletion)
      .map(toFixedValue(NoContentResponse::noContent))
      .onComplete(context::write, context::write);
  }

  @Override
  void getMany(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    final var requestRepository = RequestRepository.using(clients);

    fromFutureResult(requestRepository.findBy(routingContext.request().query()))
      .map(this::mapToJson)
      .map(JsonHttpResponse::ok)
      .onComplete(context::write, context::write);
  }

  private JsonObject mapToJson(MultipleRecords<Request> requests) {
    final var requestRepresentation = new RequestRepresentation();

    return requests.asJson(requestRepresentation::extendedRepresentation, "requests");
  }

  @Override
  void empty(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    fromFutureResult(clients.requestsStorage().delete())
      .map(toFixedValue(NoContentResponse::noContent))
      .onComplete(context::write, context::write);
  }

  void move(RoutingContext routingContext) {
    final var context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);

    final var representation = routingContext.getBodyAsJson();

    final var id = getRequestId(routingContext);

    final var requestRepository = RequestRepository.using(clients);
    final var requestQueueRepository = RequestQueueRepository.using(clients);

    final var itemRepository = new ItemRepository(clients, true, true, true);
    final var loanRepository = new LoanRepository(clients);
    final var loanPolicyRepository = new LoanPolicyRepository(clients);
    final var configurationRepository = new ConfigurationRepository(clients);

    final var updateUponRequest = new UpdateUponRequest(new UpdateItem(clients),
      new UpdateLoan(clients, loanRepository, loanPolicyRepository),
      UpdateRequestQueue.using(clients));

    final var moveRequestProcessAdapter = new MoveRequestProcessAdapter(itemRepository,
      loanRepository, requestRepository, requestQueueRepository);

    final var eventPublisher = new EventPublisher(routingContext);

    final var moveRequestService = new MoveRequestService(
      requestRepository, new RequestPolicyRepository(clients),
      updateUponRequest, moveRequestProcessAdapter, new RequestLoanValidator(loanRepository),
      RequestNoticeSender.using(clients), configurationRepository, eventPublisher);

    fromFutureResult(requestRepository.getById(id))
      .map(RequestAndRelatedRecords::new)
      .map(request -> asMove(request, representation))
      .flatMapFuture(move -> moveRequestService.moveRequest(move, move.getOriginalRequest()))
      .onSuccess(records -> eventPublisher.publishDueDateChangedEvent(records, clients))
      .map(RequestAndRelatedRecords::getRequest)
      .map(new RequestRepresentation()::extendedRepresentation)
      .map(JsonHttpResponse::ok)
      .onComplete(context::write, context::write);
  }

  private RequestAndRelatedRecords asMove(RequestAndRelatedRecords requestAndRelatedRecords,
    JsonObject representation) {

    final var originalItemId = requestAndRelatedRecords.getItemId();
    final var destinationItemId = representation.getString("destinationItemId");

    if (representation.containsKey("requestType")) {
      RequestType requestType = RequestType.from(representation.getString("requestType"));
      return requestAndRelatedRecords.withRequestType(requestType).asMove(originalItemId, destinationItemId);
    }

    return requestAndRelatedRecords.asMove(originalItemId, destinationItemId);
  }

  private ProxyRelationshipValidator createProxyRelationshipValidator(
    JsonObject representation, Clients clients) {

    return new ProxyRelationshipValidator(clients, () ->
      singleValidationError("proxyUserId is not valid",
        PROXY_USER_ID, representation.getString(PROXY_USER_ID)));
  }

  private String getRequestId(RoutingContext routingContext) {
    return routingContext.request().getParam("id");
  }
}
