package org.folio.circulation.domain;

import static org.folio.circulation.domain.Request.from;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;

import java.net.URLEncoder;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.SingleRecordMapper;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class RequestRepository {
  private final CollectionResourceClient requestsStorageClient;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  

  private RequestRepository(
    CollectionResourceClient requestsStorageClient,
    ItemRepository itemRepository,
    UserRepository userRepository,
    LoanRepository loanRepository) {

    this.requestsStorageClient = requestsStorageClient;
    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
    this.loanRepository = loanRepository;
  }

  public static RequestRepository using(Clients clients) {
    return new RequestRepository(clients.requestsStorage(),
      new ItemRepository(clients, true, false),
      new UserRepository(clients), new LoanRepository(clients));
  }

  public CompletableFuture<HttpResult<MultipleRecords<Request>>> findBy(String query) {
    return requestsStorageClient.getMany(query)
      .thenApply(this::mapResponseToRequests)
      .thenComposeAsync(result -> itemRepository.fetchItemsFor(result, Request::withItem))
      .thenComposeAsync(result -> result.after(loanRepository::findOpenLoansFor));
  }

  //TODO: try to consolidate this further with above
  CompletableFuture<HttpResult<MultipleRecords<Request>>> findBy(
    String query,
    Integer pageLimit) {

    return requestsStorageClient.getMany(query, pageLimit, 0)
      .thenApply(this::mapResponseToRequests)
      .thenComposeAsync(requests ->
        itemRepository.fetchItemsFor(requests, Request::withItem));
  }

  private HttpResult<MultipleRecords<Request>> mapResponseToRequests(Response response) {
    return MultipleRecords.from(response, Request::from, "requests");
  }

  public CompletableFuture<HttpResult<Boolean>> exists(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return exists(requestAndRelatedRecords.getRequest());
  }

  private CompletableFuture<HttpResult<Boolean>> exists(Request request) {
    return exists(request.getId());
  }

  private CompletableFuture<HttpResult<Boolean>> exists(String id) {
    return new SingleRecordFetcher<>(requestsStorageClient, "request",
      new SingleRecordMapper<>(request -> true, response -> {
        if (response.getStatusCode() == 404) {
          return HttpResult.succeeded(false);
        } else {
          return HttpResult.failed(new ForwardOnFailure(response));
        }
      }))
      .fetch(id);
  }

  public CompletableFuture<HttpResult<Integer>> getRequestCount(String itemId) {
    if(itemId == null) {
      CompletableFuture c = new CompletableFuture<HttpResult<Integer>>();
      HttpResult<Integer> h = HttpResult.succeeded(0);
      c.complete(h);
      return c;
    }
    String requestsQuery = URLEncoder.encode(String.format(
        "itemId==%s AND status=Open", itemId));
   
    return requestsStorageClient.getMany(requestsQuery)
        .thenApply(response -> {
          try {
            if(response.getStatusCode() != 200) {
              return HttpResult.failed(new ForwardOnFailure(response));
            } else {
              JsonObject responseJson = response.getJson();
              Integer totalResults = responseJson.getInteger("totalRecords");
              if(totalResults != null) {
                return HttpResult.succeeded(totalResults);
              } else {
                return HttpResult.failed(new ForwardOnFailure(response));
              }
            }
          } catch(Exception e) {
            return HttpResult.failed(new ForwardOnFailure(response));
          }
        });
  }
  
  public CompletableFuture<HttpResult<Request>> getById(String id) {
    return fetchRequest(id)
      .thenComposeAsync(result -> result.combineAfter(itemRepository::fetchFor,
        Request::withItem))
      .thenComposeAsync(this::fetchRequester)
      .thenComposeAsync(this::fetchProxy);
  }

  private CompletableFuture<HttpResult<Request>> fetchRequest(String id) {
    return new SingleRecordFetcher<>(requestsStorageClient, "request", Request::from)
      .fetch(id);
  }

  //TODO: May need to fetch updated representation of request
  public CompletableFuture<HttpResult<Request>> update(Request request) {
    final JsonObject representation = new RequestRepresentation()
      .storedRequest(request);

    return requestsStorageClient.put(request.getId(), representation)
      .thenApply(response -> {
        if(response.getStatusCode() == 204) {
          return succeeded(request);
        }
        else {
          return failed(new ForwardOnFailure(response));
        }
    });
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> update(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return update(requestAndRelatedRecords.getRequest())
      .thenApply(r -> r.map(requestAndRelatedRecords::withRequest));
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> create(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    JsonObject representation = new RequestRepresentation()
      .storedRequest(request);

    return requestsStorageClient.post(representation)
      .thenApply(response -> {
        if (response.getStatusCode() == 201) {
          return succeeded(requestAndRelatedRecords.withRequest(from(response.getJson())));
        } else {
          return failed(new ForwardOnFailure(response));
        }
    });
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> delete(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return delete(requestAndRelatedRecords.getRequest())
      .thenApply(r -> r.map(requestAndRelatedRecords::withRequest));
  }

  public CompletableFuture<HttpResult<Request>> delete(Request request) {
    return requestsStorageClient.delete(request.getId())
      .thenApply(response -> {
        if(response.getStatusCode() == 204) {
          return succeeded(request);
        }
        else {
          return failed(new ForwardOnFailure(response));
        }
    });
  }

  //TODO: Check if need to request requester
  private CompletableFuture<HttpResult<Request>> fetchRequester(HttpResult<Request> result) {
    return result.combineAfter(request ->
      getUser(request.getUserId()), Request::withRequester);
  }

  //TODO: Check if need to request proxy
  private CompletableFuture<HttpResult<Request>> fetchProxy(HttpResult<Request> result) {
    return result.combineAfter(request ->
      getUser(request.getProxyUserId()), Request::withProxy);
  }
  
  private CompletableFuture<HttpResult<Request>> fetchLoan(HttpResult<Request> result) {
    return result.combineAfter(request ->
        loanRepository.findOpenLoanById(request), Request::withLoan);
  }

  private CompletableFuture<HttpResult<User>> getUser(String proxyUserId) {
    return userRepository.getUser(proxyUserId);
  }
  
}
