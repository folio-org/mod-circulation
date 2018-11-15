package org.folio.circulation.domain;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlHelper;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
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
  
  CompletableFuture<HttpResult<MultipleRecords<Request>>> findUsersForRequests(
    MultipleRecords<Request> multipleRequests) {

    Collection<Request> requests = multipleRequests.getRecords();

    final List<String> usersToFetch = requests.stream()
      .map(this::getUsersFromRequest)
      .flatMap(Collection::stream)
      .distinct()
      .collect(Collectors.toList());

    final String query = CqlHelper.multipleRecordsCqlQuery(usersToFetch);

    return usersStorageClient.getMany(query)
      .thenApply(this::mapResponseToUsers)
      .thenApply(multipleUsersResult -> multipleUsersResult.next(
        multipleUsers -> HttpResult.of(() -> multipleRequests.mapRecords(
          request -> {
          Request newRequest = request;
          String requesterId = newRequest.getUserId() != null ?
          newRequest.getUserId() : "";
          String proxyId = newRequest.getProxyUserId() != null ?
          newRequest.getProxyUserId() : "";
          for(User user : multipleUsers.getRecords()) {
            if(requesterId.equals(user.getId())) {
              newRequest = newRequest.withRequester(user);
            }
            if(proxyId.equals(user.getId())) {
              newRequest = newRequest.withProxy(user);
            }
          }

          return newRequest;
        }))));
  }

  private ArrayList<String> getUsersFromRequest(Request request) {
    final ArrayList<String> usersToFetch = new ArrayList<>();

    if(request.getUserId() != null) {
      usersToFetch.add(request.getUserId());
    }

    if(request.getProxyUserId() != null) {
      usersToFetch.add(request.getProxyUserId());
    }

    return usersToFetch;
  }

  private HttpResult<MultipleRecords<User>> mapResponseToUsers(Response response) {
    return MultipleRecords.from(response, User::from, "users");
  }
}
