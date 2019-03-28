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
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.domain.representations.RequestProperties;
import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.domain.validation.UniqRequestValidator;
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

    final UserRepository userRepository = new UserRepository(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    final CreateRequestService createRequestService = new CreateRequestService(
      RequestRepository.using(clients),
      new UpdateItem(clients),
      new UpdateLoanActionHistory(clients),
      new UpdateLoan(clients, loanRepository, new LoanPolicyRepository(clients)),
      new RequestPolicyRepository(clients),
      getUniqRequestValidator()
    );

    final RequestFromRepresentationService requestFromRepresentationService =
      new RequestFromRepresentationService(
        new ItemRepository(clients, true, false),
        RequestQueueRepository.using(clients),
        userRepository,
        loanRepository,
        new ServicePointRepository(clients),
        createProxyRelationshipValidator(representation, clients),
        new ServicePointPickupLocationValidator()
      );

    requestFromRepresentationService.getRequestFrom(representation)
      .thenComposeAsync(r -> r.after(createRequestService::createRequest))
      .thenApply(r -> r.map(RequestAndRelatedRecords::getRequest))
      .thenApply(r -> r.map(new RequestRepresentation()::extendedRepresentation))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void replace(RoutingContext routingContext) {

    JsonObject representation = routingContext.getBodyAsJson();
    write(representation, "id", getId(routingContext));

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final RequestRepository requestRepository = RequestRepository.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    final CreateRequestService createRequestService = new CreateRequestService(
      requestRepository,
      new UpdateItem(clients),
      new UpdateLoanActionHistory(clients),
      new UpdateLoan(clients, loanRepository, new LoanPolicyRepository(clients)),
      new RequestPolicyRepository(clients),
      getUniqRequestValidator()
    );

    final UpdateRequestService updateRequestService = new UpdateRequestService(
      requestRepository,
      UpdateRequestQueue.using(clients),
      new ClosedRequestValidator(RequestRepository.using(clients))
    );

    final RequestFromRepresentationService requestFromRepresentationService =
      new RequestFromRepresentationService(
        new ItemRepository(clients, false, false),
        RequestQueueRepository.using(clients),
        new UserRepository(clients),
        loanRepository,
        new ServicePointRepository(clients),
        createProxyRelationshipValidator(representation, clients),
        new ServicePointPickupLocationValidator()
      );

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

    String id = getId(routingContext);

    requestRepository.getById(id)
      .thenApply(r -> r.map(new RequestRepresentation()::extendedRepresentation))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = getId(routingContext);

    final RequestRepository requestRepository = RequestRepository.using(clients);

    final UpdateRequestQueue updateRequestQueue = new UpdateRequestQueue(
      RequestQueueRepository.using(clients),
      requestRepository,
      new ServicePointRepository(clients)
    );

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
      .thenApply(r -> r.map(requests -> requests.asJson(requestRepresentation::extendedRepresentation, "requests")))
      .thenApply(OkJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.requestsStorage()
      .delete()
      .thenApply(NoContentHttpResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private ProxyRelationshipValidator createProxyRelationshipValidator(
    JsonObject representation,
    Clients clients) {

    return new ProxyRelationshipValidator(clients,
      () -> failure(
        "proxyUserId is not valid",
        RequestProperties.PROXY_USER_ID,
        representation.getString(RequestProperties.PROXY_USER_ID)
      )
    );
  }

  private String getId(RoutingContext routingContext) {
    return routingContext.request().getParam("id");
  }

  private UniqRequestValidator getUniqRequestValidator() {
    return new UniqRequestValidator(
      request -> failure("This requester already has an open request for this item")
    );
  }
}
