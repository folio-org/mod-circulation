package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.representations.RequestProperties;
import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.WebContext;

import java.util.concurrent.CompletableFuture;

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

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, true, false);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final UserRepository userRepository = new UserRepository(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateLoanActionHistory updateLoanActionHistory = new UpdateLoanActionHistory(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator =
      createProxyRelationshipValidator(representation, clients);

    final RequestRepresentation requestRepresentation = new RequestRepresentation();

    completedFuture(succeeded(representation))
      .thenApply(r -> r.next(this::validateStatus))
      .thenApply(r -> r.map(this::removeRelatedRecordInformation))
      .thenApply(r -> r.map(Request::from))
      .thenComposeAsync(r -> r.combineAfter(itemRepository::fetchFor, Request::withItem))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getUser, Request::withRequester))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getProxyUser, Request::withProxy))
      .thenApply(r -> r.map(RequestAndRelatedRecords::new))
      .thenComposeAsync(r -> r.combineAfter(requestQueueRepository::get,
        RequestAndRelatedRecords::withRequestQueue))
      .thenApply(this::refuseWhenItemDoesNotExist)
      .thenApply(this::refuseWhenItemIsNotValid)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenComposeAsync(r -> r.after(this::setRequestQueuePosition))
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

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, false, false);
    final UserRepository userRepository = new UserRepository(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final UpdateRequestQueue updateRequestQueue = UpdateRequestQueue.using(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateLoanActionHistory updateLoanActionHistory = new UpdateLoanActionHistory(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator =
      createProxyRelationshipValidator(representation, clients);

    final ClosedRequestValidator closedRequestValidator = new ClosedRequestValidator(
      RequestRepository.using(clients));

    String id = routingContext.request().getParam("id");
    write(representation, "id", id);

    final CompletableFuture<HttpResult<RequestAndRelatedRecords>> setupFuture = completedFuture(succeeded(representation))
      .thenApply(r -> r.next(this::validateStatus))
      .thenApply(r -> r.map(this::removeRelatedRecordInformation))
      .thenApply(r -> r.map(Request::from))
      .thenComposeAsync(r -> r.combineAfter(itemRepository::fetchFor, Request::withItem))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getUser, Request::withRequester))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getProxyUser, Request::withProxy))
      .thenApply(r -> r.map(RequestAndRelatedRecords::new))
      .thenComposeAsync(r -> r.combineAfter(requestQueueRepository::get,
        RequestAndRelatedRecords::withRequestQueue));

    final CompletableFuture<HttpResult<RequestAndRelatedRecords>> processingFuture = setupFuture.thenComposeAsync(r ->
      r.after(requestAndRelatedRecords -> {
        final String requestId = requestAndRelatedRecords.getRequest().getId();

        return requestRepository.exists(requestId).thenCompose(
          r2 -> r2.after(exists -> {
            if (exists) {
              return r.after(requestAndRelatedRecords2 ->
                setRequestQueuePositionWhenNew(requestAndRelatedRecords2, requestRepository)
                  .thenComposeAsync(r3 -> r3.after(closedRequestValidator::refuseWhenAlreadyClosed))
                  .thenComposeAsync(r3 -> r3.after(proxyRelationshipValidator::refuseWhenInvalid))
                  .thenApply(r3 -> r3.next(this::removeRequestQueuePositionWhenCancelled))
                  .thenComposeAsync(r3 -> r3.after(requestRepository::update))
                  .thenComposeAsync(r3 -> r3.after(updateRequestQueue::onCancellation)));
            } else {
              return r.after(requestAndRelatedRecords2 ->
                setRequestQueuePosition(requestAndRelatedRecords2)
                  .thenComposeAsync(r3 -> r3.after(updateItem::onRequestCreation))
                  .thenComposeAsync(r3 -> r3.after(updateLoanActionHistory::onRequestCreation))
                  .thenComposeAsync(r3 -> r3.after(requestRepository::create)));
            }
          }));
      }));

    processingFuture
      .thenApply(NoContentHttpResult::from)
      .thenAccept(r2 -> r2.writeTo(routingContext.response()));
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

  private JsonObject removeRelatedRecordInformation(JsonObject request) {
    request.remove("item");
    request.remove("requester");
    request.remove("proxy");

    return request;
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

  private CompletableFuture<HttpResult<RequestAndRelatedRecords>> setRequestQueuePosition(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    //TODO: Extract to method to add to queue
    requestAndRelatedRecords.withRequest(requestAndRelatedRecords.getRequest()
      .changePosition(requestAndRelatedRecords.getRequestQueue().nextAvailablePosition()));

    return completedFuture(succeeded(requestAndRelatedRecords));
  }

  private CompletableFuture<HttpResult<RequestAndRelatedRecords>> setRequestQueuePositionWhenNew(
    RequestAndRelatedRecords requestAndRelatedRecords, RequestRepository requestRepository) {

    final String requestId = requestAndRelatedRecords.getRequest().getId();

    //Should not fail if request does not exist (and so we are creating)
    //TODO: Branch the behaviour between creating at specific location and
    return requestRepository.exists(requestId).thenCompose(
      r -> r.after(exists -> {
        if (exists) {
          return completedFuture(succeeded(requestAndRelatedRecords));
        }

        //TODO: Extract to method to add to queue
        requestAndRelatedRecords.withRequest(requestAndRelatedRecords.getRequest()
          .changePosition(requestAndRelatedRecords.getRequestQueue().nextAvailablePosition()));

        return completedFuture(succeeded(requestAndRelatedRecords));
      }));
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

  private HttpResult<JsonObject> validateStatus(JsonObject representation) {
    RequestStatus status = RequestStatus.from(representation);

    if(!status.isValid()) {
      //TODO: Replace this with validation error
      // (but don't want to change behaviour at the moment)
      return failed(new BadRequestFailure(RequestStatus.invalidStatusErrorMessage()));
    }
    else {
      status.writeTo(representation);
      return succeeded(representation);
    }
  }

  private ProxyRelationshipValidator createProxyRelationshipValidator(JsonObject representation, Clients clients) {
    return new ProxyRelationshipValidator(clients, () -> failure(
      "proxyUserId is not valid", RequestProperties.PROXY_USER_ID,
      representation.getString(RequestProperties.PROXY_USER_ID)));
  }
}
