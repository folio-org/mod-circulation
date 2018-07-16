package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.representations.RequestProperties;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.WebContext;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.*;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class RequestCollectionResource extends CollectionResource {
  public RequestCollectionResource(HttpClient client) {
    super(client, "/circulation/requests");
  }

  void create(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject representation = routingContext.getBodyAsJson();

    RequestStatus status = RequestStatus.from(representation);

    HttpServerResponse response = routingContext.response();
    if(!status.isValid()) {
      ClientErrorResponse.badRequest(response,
        RequestStatus.invalidStatusErrorMessage());
      return;
    }
    else {
      status.writeTo(representation);
    }

    removeRelatedRecordInformation(representation);

    final Request request = Request.from(representation);

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, true, false);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final UserRepository userRepository = new UserRepository(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateLoanActionHistory updateLoanActionHistory = new UpdateLoanActionHistory(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> failure(
      "proxyUserId is not valid", RequestProperties.PROXY_USER_ID,
      request.getProxyUserId()));

    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    completedFuture(succeeded(new RequestAndRelatedRecords(request)))
      .thenCombineAsync(itemRepository.fetchFor(request), this::addItem)
      .thenApply(this::refuseWhenItemDoesNotExist)
      .thenApply(this::refuseWhenItemIsNotValid)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.get(request.getItemId()), this::addRequestQueue)
      .thenCombineAsync(userRepository.getUser(request.getUserId(), false), this::addUser)
      .thenCombineAsync(userRepository.getUser(request.getProxyUserId(), false), this::addProxyUser)
      .thenApply(r -> r.next(this::setRequestQueuePosition))
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreation))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreation))
      .thenComposeAsync(r -> r.after(requestRepository::create))
      .thenApply(r -> r.map(RequestAndRelatedRecords::getRequest))
      .thenApply(r -> r.map(requestRepresentation::extendedRepresentation))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void replace(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject representation = routingContext.getBodyAsJson();

    removeRelatedRecordInformation(representation);

    String id = routingContext.request().getParam("id");
    write(representation, "id", id);

    final Request request = Request.from(representation);

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, false, false);
    final UserRepository userRepository = new UserRepository(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final UpdateRequestQueue updateRequestQueue = UpdateRequestQueue.using(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> failure(
      "proxyUserId is not valid", RequestProperties.PROXY_USER_ID,
      request.getProxyUserId()));

    completedFuture(succeeded(new RequestAndRelatedRecords(request)))
      .thenCombineAsync(itemRepository.fetchFor(request), this::addItem)
      .thenCombineAsync(userRepository.getUser(request.getUserId(), false), this::addUser)
      .thenCombineAsync(userRepository.getUser(request.getProxyUserId(), false), this::addProxyUser)
      .thenCombineAsync(requestQueueRepository.get(request.getItemId()), this::addRequestQueue)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenApply(r -> r.next(this::removeRequestQueuePositionWhenCancelled))
      .thenComposeAsync(result -> result.after(requestRepository::update))
      .thenComposeAsync(r -> r.after(updateRequestQueue::onCancellation))
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

    clients.requestsStorage().delete(id)
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

  private void removeRelatedRecordInformation(JsonObject request) {
    request.remove("item");
    request.remove("requester");
    request.remove("proxy");
  }

  private HttpResult<RequestAndRelatedRecords> addItem(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<Item> item) {

    return HttpResult.combine(loanResult, item,
      RequestAndRelatedRecords::withItem);
  }

  private HttpResult<RequestAndRelatedRecords> addRequestQueue(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<RequestQueue> requestQueueResult) {

    return HttpResult.combine(loanResult, requestQueueResult,
      RequestAndRelatedRecords::withRequestQueue);
  }

  private HttpResult<RequestAndRelatedRecords> addUser(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      RequestAndRelatedRecords::withRequestingUser);
  }

  private HttpResult<RequestAndRelatedRecords> addProxyUser(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      RequestAndRelatedRecords::withProxyUser);
  }

  private HttpResult<RequestAndRelatedRecords> refuseWhenItemDoesNotExist(
    HttpResult<RequestAndRelatedRecords> result) {

    return result.next(requestAndRelatedRecords -> {
      if(requestAndRelatedRecords.getRequest().getItem().isNotFound()) {
        return failed(failure(
          "Item does not exist", "itemId",
          requestAndRelatedRecords.getRequest().getItemId()));
      }
      else {
        return result;
      }
    });
  }

  private HttpResult<RequestAndRelatedRecords> refuseWhenItemIsNotValid(
    HttpResult<RequestAndRelatedRecords> result) {

    return result.next(requestAndRelatedRecords -> {
      Request request = requestAndRelatedRecords.getRequest();

      RequestType requestType = RequestType.from(request);

      if (!requestType.canCreateRequestForItem(request.getItem())) {
        return failed(failure(
          String.format("Item is not %s, %s or %s", CHECKED_OUT,
            CHECKED_OUT_HELD, CHECKED_OUT_RECALLED),
          "itemId", request.getItemId()
        ));
      }
      else {
        return result;
      }
    });
  }

  private HttpResult<RequestAndRelatedRecords> setRequestQueuePosition(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    //TODO: Extract to method to add to queue
    requestAndRelatedRecords.withRequest(requestAndRelatedRecords.getRequest()
      .changePosition(requestAndRelatedRecords.getRequestQueue().nextAvailablePosition()));

    return succeeded(requestAndRelatedRecords);
  }

  private HttpResult<RequestAndRelatedRecords> removeRequestQueuePositionWhenCancelled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    //TODO: Extract to cancel method
    if(request.isCancelled()) {
      requestAndRelatedRecords.getRequestQueue().remove(request);
    }

    return succeeded(requestAndRelatedRecords);
  }
}
