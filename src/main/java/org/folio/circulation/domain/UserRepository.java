package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.ValidationError;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserRepository {
  private final CollectionResourceClient usersStorageClient;
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public UserRepository(Clients clients) {
    usersStorageClient = clients.usersStorage();
  }

  public CompletableFuture<HttpResult<User>> getUser(UserRelatedRecord userRelatedRecord) {
    return getUser(userRelatedRecord.getUserId());
  }

  public CompletableFuture<HttpResult<User>> getProxyUser(UserRelatedRecord userRelatedRecord) {
    return getUser(userRelatedRecord.getProxyUserId());
  }

  CompletableFuture<HttpResult<User>> getUser(String userId) {
    return FetchSingleRecord.<User>forRecord("user")
      .using(usersStorageClient)
      .mapTo(User::new)
      .whenNotFound(succeeded(null))
      .fetch(userId);
  }

  //TODO: Replace this with validator
  public CompletableFuture<HttpResult<User>> getUserFailOnNotFound(String userId) {
    return FetchSingleRecord.<User>forRecord("user")
      .using(usersStorageClient)
      .mapTo(User::new)
      .whenNotFound(failed(new ValidationErrorFailure(
        new ValidationError("user is not found", "userId", userId))))
      .fetch(userId);
  }

  public CompletableFuture<HttpResult<User>> getProxyUserByBarcode(String barcode) {
    //Not proxying, so no need to get proxy user
    if(StringUtils.isBlank(barcode)) {
      return CompletableFuture.completedFuture(succeeded(null));
    }
    else {
      return getUserByBarcode(barcode, "proxyUserBarcode");
    }
  }

  public CompletableFuture<HttpResult<User>> getUserByBarcode(String barcode) {
    return getUserByBarcode(barcode, "userBarcode");
  }

  private CompletableFuture<HttpResult<User>> getUserByBarcode(
    String barcode,
    String propertyName) {

    return usersStorageClient.getMany(String.format("barcode==%s", barcode), 1, 0)
      .thenApply(response -> MultipleRecords.from(response, User::new, "users")
        .map(MultipleRecords::getRecords)
        .map(users -> users.stream().findFirst())
        .next(user -> user.map(HttpResult::succeeded).orElseGet(() -> failed(failure(
          "Could not find user with matching barcode", propertyName, barcode)))));
  }
  
  public CompletableFuture<HttpResult<MultipleRecords<Request>>> 
    findUsersForRequests(MultipleRecords<Request> multipleRequests) {
    Collection<Request> requests = multipleRequests.getRecords();
    List<String> clauses = new ArrayList<>();
    
    for(Request request : requests) {
      String requesterId = request.getUserId();
      String proxyId = request.getProxyUserId();
      if(requesterId != null) {
        clauses.add(String.format("( id==%s )", requesterId));
      }
      if(proxyId != null) {
        clauses.add(String.format("( id==%s )", proxyId));
      }
    }
    if(clauses.isEmpty()) {
      log.info("No users to query");
      return CompletableFuture.completedFuture(HttpResult.succeeded(multipleRequests));
    }
    final String usersQuery = String.join(" OR ", clauses);
    log.info(String.format("Querying users with query %s", usersQuery));
    HttpResult<String> queryResult = CqlHelper.encodeQuery(usersQuery);
    return queryResult.after(query -> usersStorageClient.getMany(query)
      .thenApply(this::mapResponseToUsers)
      .thenApply(multipleUsersResult -> multipleUsersResult.next(
        multipleUsers -> {
          List<Request> newRequestList = new ArrayList<>();
          Collection<User> userCollection = multipleUsers.getRecords();
          for(Request request : requests) {
            Request newRequest = request;
            String requesterId = newRequest.getUserId() != null ?
            newRequest.getUserId() : "";
            String proxyId = newRequest.getProxyUserId() != null ?
            newRequest.getProxyUserId() : "";
            for(User user : userCollection) {
              if(requesterId.equals(user.getId())) {
                newRequest = newRequest.withRequester(user);               
              }
              if(proxyId.equals(user.getId())) {
                newRequest = newRequest.withProxy(user);               
              }
            }            
            newRequestList.add(newRequest);
          }
          return HttpResult.succeeded(new MultipleRecords<>(newRequestList, multipleRequests.getTotalRecords()));
        })));
  }
  
  private HttpResult<MultipleRecords<User>> mapResponseToUsers(Response response) {
    return MultipleRecords.from(response, User::from, "users");
  }
}
