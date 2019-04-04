package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlHelper;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;

public class UserRepository {
  private final CollectionResourceClient usersStorageClient;

  public UserRepository(Clients clients) {
    usersStorageClient = clients.usersStorage();
  }

  public CompletableFuture<Result<User>> getUser(UserRelatedRecord userRelatedRecord) {
    return getUser(userRelatedRecord.getUserId());
  }

  public CompletableFuture<Result<User>> getProxyUser(UserRelatedRecord userRelatedRecord) {
    return getUser(userRelatedRecord.getProxyUserId());
  }

  public CompletableFuture<Result<User>> getUser(String userId) {
    return FetchSingleRecord.<User>forRecord("user")
      .using(usersStorageClient)
      .mapTo(User::new)
      .whenNotFound(succeeded(null))
      .fetch(userId);
  }

  //TODO: Replace this with validator
  public CompletableFuture<Result<User>> getUserFailOnNotFound(String userId) {
    return FetchSingleRecord.<User>forRecord("user")
      .using(usersStorageClient)
      .mapTo(User::new)
      .whenNotFound(failedValidation("user is not found", "userId", userId))
      .fetch(userId);
  }

  public CompletableFuture<Result<User>> getProxyUserByBarcode(String barcode) {
    //Not proxying, so no need to get proxy user
    if(StringUtils.isBlank(barcode)) {
      return completedFuture(succeeded(null));
    }
    else {
      return getUserByBarcode(barcode, "proxyUserBarcode");
    }
  }

  public CompletableFuture<Result<User>> getUserByBarcode(String barcode) {
    return getUserByBarcode(barcode, "userBarcode");
  }

  private CompletableFuture<Result<User>> getUserByBarcode(
    String barcode,
    String propertyName) {

    return usersStorageClient.getMany(String.format("barcode==%s", barcode), 1, 0)
      .thenApply(response -> MultipleRecords.from(response, User::new, "users")
        .map(MultipleRecords::getRecords)
        .map(users -> users.stream().findFirst())
        .next(user -> user.map(Result::succeeded).orElseGet(() ->
          failedValidation("Could not find user with matching barcode",
            propertyName, barcode))));
  }

  CompletableFuture<Result<MultipleRecords<Request>>> findUsersForRequests(
    MultipleRecords<Request> multipleRequests) {

    Collection<Request> requests = multipleRequests.getRecords();

    final List<String> usersToFetch = requests.stream()
      .map(this::getUsersFromRequest)
      .flatMap(Collection::stream)
      .distinct()
      .collect(Collectors.toList());

    if (usersToFetch.isEmpty()) {
      return completedFuture(succeeded(multipleRequests));
    }

    final String query = CqlHelper.multipleRecordsCqlQuery(usersToFetch);

    return usersStorageClient.getMany(query, requests.size(), 0)
      .thenApply(this::mapResponseToUsers)
      .thenApply(multipleUsersResult -> multipleUsersResult.next(
        multipleUsers -> of(() ->
          multipleRequests.mapRecords(request ->
            matchUsersToRequests(request, multipleUsers)))));
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

  private Request matchUsersToRequests(
    Request request,
    MultipleRecords<User> users) {

    final Map<String, User> userMap = users.toMap(User::getId);

    return request
      .withRequester(userMap.getOrDefault(request.getUserId(), null))
      .withProxy(userMap.getOrDefault(request.getProxyUserId(), null));
  }


  private Result<MultipleRecords<User>> mapResponseToUsers(Response response) {
    return MultipleRecords.from(response, User::from, "users");
  }
}
