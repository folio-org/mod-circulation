package org.folio.circulation.resources;

import static org.folio.circulation.domain.representations.RequestProperties.PROXY_USER_ID;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

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
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.OkJsonResponseResult;
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

    final PatronNoticePolicyRepository noticePolicyRepository = new PatronNoticePolicyRepository(clients);
    final PatronNoticeService patronNoticeService = new PatronNoticeService(noticePolicyRepository, clients);

    final CreateRequestService createRequestService = new CreateRequestService(
      RequestRepository.using(clients),
      new UpdateItem(clients),
      new UpdateLoanActionHistory(clients),
      new UpdateLoan(clients, loanRepository, new LoanPolicyRepository(clients)),
      new RequestPolicyRepository(clients),
      loanRepository, patronNoticeService);

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
      .thenApply(CreatedJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void replace(RoutingContext routingContext) {

    JsonObject representation = routingContext.getBodyAsJson();
    write(representation, "id", getRequestId(routingContext));

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final RequestRepository requestRepository = RequestRepository.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    final PatronNoticePolicyRepository noticePolicyRepository = new PatronNoticePolicyRepository(clients);
    final PatronNoticeService patronNoticeService = new PatronNoticeService(noticePolicyRepository, clients);

    final CreateRequestService createRequestService = new CreateRequestService(
      requestRepository,
      new UpdateItem(clients),
      new UpdateLoanActionHistory(clients),
      new UpdateLoan(clients, loanRepository, new LoanPolicyRepository(clients)),
      new RequestPolicyRepository(clients),
      loanRepository, patronNoticeService);

    final UpdateRequestService updateRequestService = new UpdateRequestService(
      requestRepository,
      UpdateRequestQueue.using(clients),
      new ClosedRequestValidator(RequestRepository.using(clients)),
      patronNoticeService);

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
      .thenApply(NoContentResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  void get(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    final RequestRepository requestRepository = RequestRepository.using(clients);

    String id = getRequestId(routingContext);

    requestRepository.getById(id)
      .thenApply(r -> r.map(new RequestRepresentation()::extendedRepresentation))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = getRequestId(routingContext);

    final RequestRepository requestRepository = RequestRepository.using(clients);

    final UpdateRequestQueue updateRequestQueue = new UpdateRequestQueue(
      RequestQueueRepository.using(clients),
      requestRepository,
      new ServicePointRepository(clients)
    );

    requestRepository.getById(id)
      .thenComposeAsync(r -> r.after(requestRepository::delete))
      .thenComposeAsync(r -> r.after(updateRequestQueue::onDeletion))
      .thenApply(NoContentResult::from)
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
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.requestsStorage().delete()
      .thenApply(NoContentResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private ProxyRelationshipValidator createProxyRelationshipValidator(
    JsonObject representation,
    Clients clients) {

    return new ProxyRelationshipValidator(clients, () ->
      singleValidationError("proxyUserId is not valid",
        PROXY_USER_ID, representation.getString(PROXY_USER_ID)));
  }

  private String getRequestId(RoutingContext routingContext) {
    return routingContext.request().getParam("id");
  }
}
