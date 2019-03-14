package org.folio.circulation.resources;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import org.folio.circulation.domain.CreateRequestService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateLoan;
import org.folio.circulation.domain.UpdateLoanActionHistory;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.UpdateRequestService;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.representations.RequestProperties;
import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonHttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.NoContentHttpResult;
import org.folio.circulation.support.OkJsonHttpResult;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class RequestCollectionResource extends CollectionResource {
  public RequestCollectionResource(HttpClient client) {
    super(client, "/circulation/requests");
  }

  void create(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject representation = routingContext.getBodyAsJson();

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, true, false);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final UserRepository userRepository = new UserRepository(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final RequestPolicyRepository requestPolicyRepository = new RequestPolicyRepository(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateLoanActionHistory updateLoanActionHistory = new UpdateLoanActionHistory(clients);
    final UpdateLoan updateLoan = new UpdateLoan(clients, loanRepository, loanPolicyRepository);
    final ServicePointPickupLocationValidator servicePointPickupLocationValidator
        = new ServicePointPickupLocationValidator();

    final ProxyRelationshipValidator proxyRelationshipValidator =
      createProxyRelationshipValidator(representation, clients);

    final CreateRequestService createRequestService = new CreateRequestService(
      requestRepository, updateItem, updateLoanActionHistory, updateLoan, requestPolicyRepository);

    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    final RequestFromRepresentationService requestFromRepresentationService
      = new RequestFromRepresentationService(itemRepository, requestQueueRepository,
          userRepository, loanRepository, servicePointRepository,
          proxyRelationshipValidator, servicePointPickupLocationValidator);

    requestFromRepresentationService.getRequestFrom(representation)
      .thenComposeAsync(r -> r.after(createRequestService::createRequest))
      .thenApply(r -> r.map(RequestAndRelatedRecords::getRequest))
      .thenApply(r -> r.map(requestRepresentation::extendedRepresentation))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void replace(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject representation = routingContext.getBodyAsJson();

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, false, false);
    final UserRepository userRepository = new UserRepository(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final RequestPolicyRepository requestPolicyRepository =  new RequestPolicyRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final UpdateRequestQueue updateRequestQueue = UpdateRequestQueue.using(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateLoan updateLoan = new UpdateLoan(clients, loanRepository, loanPolicyRepository);
    final UpdateLoanActionHistory updateLoanActionHistory = new UpdateLoanActionHistory(clients);
    final ServicePointPickupLocationValidator servicePointPickupLocationValidator
        = new ServicePointPickupLocationValidator();

    final ProxyRelationshipValidator proxyRelationshipValidator =
      createProxyRelationshipValidator(representation, clients);

    final ClosedRequestValidator closedRequestValidator = new ClosedRequestValidator(
      RequestRepository.using(clients));

    final CreateRequestService createRequestService = new CreateRequestService(
      requestRepository, updateItem, updateLoanActionHistory, updateLoan, requestPolicyRepository);


    final UpdateRequestService updateRequestService = new UpdateRequestService(
      requestRepository, updateRequestQueue, closedRequestValidator);

    String id = routingContext.request().getParam("id");
    write(representation, "id", id);

    final RequestFromRepresentationService requestFromRepresentationService
      = new RequestFromRepresentationService(itemRepository,
        requestQueueRepository, userRepository, loanRepository, servicePointRepository,
          proxyRelationshipValidator, servicePointPickupLocationValidator);

    requestFromRepresentationService.getRequestFrom(representation)
      .thenComposeAsync(r -> r.afterWhen(requestRepository::exists,
        updateRequestService::replaceRequest,
        createRequestService::createRequest))
      .thenApply(NoContentHttpResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  void get(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final RequestRepository requestRepository = RequestRepository.using(clients);
    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    String id = routingContext.request().getParam("id");

    requestRepository.getById(id)
      .thenApply(r -> r.map(requestRepresentation::extendedRepresentation))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = routingContext.request().getParam("id");

    final RequestRepository requestRepository = RequestRepository.using(clients);
    final UpdateRequestQueue updateRequestQueue = new UpdateRequestQueue(
      RequestQueueRepository.using(clients), requestRepository, new ServicePointRepository(clients));

    requestRepository.getById(id)
      .thenComposeAsync(r -> r.after(requestRepository::delete))
      .thenComposeAsync(r -> r.after(updateRequestQueue::onDeletion))
      .thenApply(NoContentHttpResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final RequestRepository requestRepository = RequestRepository.using(clients);
    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    requestRepository.findBy(routingContext.request().query())
      .thenApply(r -> r.map(requests ->
        requests.asJson(requestRepresentation::extendedRepresentation, "requests")))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.requestsStorage().delete()
      .thenApply(NoContentHttpResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private ProxyRelationshipValidator createProxyRelationshipValidator(
    JsonObject representation,
    Clients clients) {

    return new ProxyRelationshipValidator(clients, () -> failure(
      "proxyUserId is not valid", RequestProperties.PROXY_USER_ID,
      representation.getString(RequestProperties.PROXY_USER_ID)));
  }
}
