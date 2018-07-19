package org.folio.circulation.domain;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.ValidationError;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class UserRepository {
  private final CollectionResourceClient usersStorageClient;

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
}
