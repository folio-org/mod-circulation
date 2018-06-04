package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.representations.RequestProperties;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.*;
import static org.folio.circulation.support.JsonPropertyCopier.copyStringIfExists;
import static org.folio.circulation.support.JsonPropertyWriter.write;

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

    final Request request = new Request(representation);

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, false, false);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final UserRepository userRepository = new UserRepository(clients);
    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateLoanActionHistory updateLoanActionHistory = new UpdateLoanActionHistory(clients);
    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> new ValidationErrorFailure(
      "proxyUserId is not valid", RequestProperties.PROXY_USER_ID,
      request.getProxyUserId()));

    completedFuture(HttpResult.success(new RequestAndRelatedRecords(request)))
      .thenCombineAsync(itemRepository.fetchFor(request), this::addInventoryRecords)
      .thenApply(this::refuseWhenItemDoesNotExist)
      .thenApply(this::refuseWhenItemIsNotValid)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueFetcher.get(request.getItemId()), this::addRequestQueue)
      .thenCombineAsync(userRepository.getUser(request.getUserId(), false), this::addUser)
      .thenCombineAsync(userRepository.getUser(request.getProxyUserId(), false), this::addProxyUser)
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreation))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreation))
      .thenComposeAsync(r -> r.after(records -> createRequest(records, clients)))
      .thenApply(r -> r.map(this::extendedRequest))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  void replace(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");
    JsonObject representation = routingContext.getBodyAsJson();

    removeRelatedRecordInformation(representation);

    final Request request = new Request(representation);

    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, false, false);
    final UserRepository userRepository = new UserRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> new ValidationErrorFailure(
      "proxyUserId is not valid", RequestProperties.PROXY_USER_ID,
      request.getProxyUserId()));

    completedFuture(HttpResult.success(new RequestAndRelatedRecords(request)))
      .thenCombineAsync(itemRepository.fetchFor(request), this::addInventoryRecords)
      .thenCombineAsync(userRepository.getUser(request.getUserId(), false), this::addUser)
      .thenCombineAsync(userRepository.getUser(request.getProxyUserId(), false), this::addProxyUser)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenAcceptAsync(result -> {
        if(result.failed()) {
          result.cause().writeTo(routingContext.response());
          return;
        }

        final Item item = result.value().getInventoryRecords();
        final JsonObject requester = result.value().getRequestingUser().asJson();
        final JsonObject proxy = result.value().getProxyUser().asJson();

        addStoredItemProperties(representation, item);
        addStoredRequesterProperties(representation, requester);
        addStoredProxyProperties(representation, proxy);

        clients.requestsStorage().put(id, representation, response -> {
          if(response.getStatusCode() == 204) {
            SuccessResponse.noContent(routingContext.response());
          }
          else {
            ForwardResponse.forward(routingContext.response(), response);
          }
        });
      });
  }

  void get(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = routingContext.request().getParam("id");

    clients.requestsStorage().get(id)
      .thenAccept(requestResponse -> {
        if(requestResponse.getStatusCode() == 200) {
          Request request = new Request(requestResponse.getJson());

          ItemRepository itemRepository = new ItemRepository(clients, false, false);

          CompletableFuture<HttpResult<Item>> inventoryRecordsCompleted =
            itemRepository.fetchFor(request);

          inventoryRecordsCompleted.thenAccept(r -> {
            if(r.failed()) {
              r.cause().writeTo(routingContext.response());
              return;
            }

            final JsonObject representation = request.asJson();

            addAdditionalItemProperties(representation, r.value());

            JsonResponse.success(routingContext.response(), representation);
          });
        }
        else {
          ForwardResponse.forward(routingContext.response(), requestResponse);
        }
      });
  }

  void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    String id = routingContext.request().getParam("id");

    clients.requestsStorage().delete(id, response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  void getMany(RoutingContext routingContext) {

    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.requestsStorage().getMany(routingContext.request().query())
      .thenAccept(requestsResponse -> {

      if(requestsResponse.getStatusCode() == 200) {
        final MultipleRecordsWrapper wrappedRequests = MultipleRecordsWrapper.fromBody(
          requestsResponse.getBody(), "requests");

        if(wrappedRequests.isEmpty()) {
          JsonResponse.success(routingContext.response(),
            wrappedRequests.toJson());

          return;
        }

        final Collection<JsonObject> requests = wrappedRequests.getRecords();

        List<String> itemIds = requests.stream()
          .map(Request::new)
          .map(Request::getItemId)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

        ItemRepository itemRepository = new ItemRepository(clients, false, false);

        CompletableFuture<HttpResult<MultipleInventoryRecords>> inventoryRecordsFetched =
          itemRepository.fetchFor(itemIds);

        //TODO: Refactor this to map to new representations
        // rather than alter the storage representations
        inventoryRecordsFetched.thenAccept(result -> {
          if(result.failed()) {
            result.cause().writeTo(routingContext.response());
            return;
          }

          final MultipleInventoryRecords records = result.value();

          requests.forEach(request -> {
              Item record = records.findRecordByItemId(
                new Request(request).getItemId());

              if(record.isFound()) {
                addAdditionalItemProperties(request, record);
              }
            });

            JsonResponse.success(routingContext.response(),
              wrappedRequests.toJson());
        });
      }
    });
  }

  void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    Clients clients = Clients.create(context, client);

    clients.requestsStorage().delete(response -> {
      if(response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      }
      else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  private void addStoredItemProperties(
    JsonObject request,
    Item item) {

    if(item == null || item.isNotFound()) {
      return;
    }

    JsonObject itemSummary = new JsonObject();

    write(itemSummary, "title", item.getTitle());
    write(itemSummary, "barcode", item.getBarcode());

    request.put("item", itemSummary);
  }

  private void addAdditionalItemProperties(
    JsonObject request,
    Item item) {

    if(item == null || item.isNotFound()) {
      return;
    }

    JsonObject itemSummary = request.containsKey("item")
      ? request.getJsonObject("item")
      : new JsonObject();

    write(itemSummary, "holdingsRecordId", item.getHoldingsRecordId());
    write(itemSummary, "instanceId", item.getInstanceId());

    request.put("item", itemSummary);
  }

  private void addStoredRequesterProperties
    (JsonObject requestWithAdditionalInformation,
     JsonObject requester) {

    if(requester == null) {
      return;
    }

    JsonObject requesterSummary = createUserSummary(requester);

    requestWithAdditionalInformation.put("requester", requesterSummary);
  }

  private void addStoredProxyProperties
    (JsonObject requestWithAdditionalInformation,
     JsonObject proxy) {

    if(proxy == null) {
      return;
    }

    JsonObject proxySummary = createUserSummary(proxy);

    requestWithAdditionalInformation.put("proxy", proxySummary);
  }

  private JsonObject createUserSummary(JsonObject user) {
    JsonObject requesterSummary = new JsonObject();

    if(user.containsKey("personal")) {
      JsonObject personalDetails = user.getJsonObject("personal");

      copyStringIfExists("lastName", personalDetails, requesterSummary);
      copyStringIfExists("firstName", personalDetails, requesterSummary);
      copyStringIfExists("middleName", personalDetails, requesterSummary);
    }

    copyStringIfExists("barcode", user, requesterSummary);
    return requesterSummary;
  }

  private void removeRelatedRecordInformation(JsonObject request) {
    request.remove("item");
    request.remove("requester");
    request.remove("proxy");
  }

  private HttpResult<RequestAndRelatedRecords> addInventoryRecords(
    HttpResult<RequestAndRelatedRecords> loanResult,
    HttpResult<Item> inventoryRecordsResult) {

    return HttpResult.combine(loanResult, inventoryRecordsResult,
      RequestAndRelatedRecords::withInventoryRecords);
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
      if(requestAndRelatedRecords.getInventoryRecords().isNotFound()) {
        return HttpResult.failure(new ValidationErrorFailure(
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

      if (!requestType.canCreateRequestForItem(requestAndRelatedRecords.getInventoryRecords())) {
        return HttpResult.failure(new ValidationErrorFailure(
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

  private CompletableFuture<HttpResult<RequestAndRelatedRecords>> createRequest(
    RequestAndRelatedRecords requestAndRelatedRecords,
    Clients clients) {

    CompletableFuture<HttpResult<RequestAndRelatedRecords>> onCreated = new CompletableFuture<>();

    JsonObject request = requestAndRelatedRecords.getRequest().asJson();

    JsonObject requestingUser = requestAndRelatedRecords.getRequestingUser().asJson();
    JsonObject proxyUser = requestAndRelatedRecords.getProxyUser().asJson();

    addStoredItemProperties(request, requestAndRelatedRecords.getInventoryRecords());
    addStoredRequesterProperties(request, requestingUser);
    addStoredProxyProperties(request, proxyUser);

    clients.requestsStorage().post(request, response -> {
      if (response.getStatusCode() == 201) {
        onCreated.complete(HttpResult.success(
          requestAndRelatedRecords.withRequest(new Request(response.getJson()))));
      } else {
        onCreated.complete(HttpResult.failure(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
  }

  private JsonObject extendedRequest(RequestAndRelatedRecords requestAndRelatedRecords) {
    final JsonObject representation = requestAndRelatedRecords.getRequest().asJson();

    addAdditionalItemProperties(representation,
      requestAndRelatedRecords.getInventoryRecords());

    return representation;
  }
}
