package org.folio.circulation.domain;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class UserRepository {
  private final CollectionResourceClient usersStorageClient;

  public UserRepository(Clients clients) {
    usersStorageClient = clients.usersStorage();
  }

  CompletableFuture<HttpResult<User>> getUser(UserRelatedRecord userRelatedRecord) {
    return getUser(userRelatedRecord.getUserId());
  }

  public CompletableFuture<HttpResult<User>> getUser(String userId) {
    return getUser(userId, true);
  }

  public CompletableFuture<HttpResult<User>> getProxyUserByBarcode(String barcode) {
    //Not proxying, so no need to get proxy user
    if(StringUtils.isBlank(barcode)) {
      return CompletableFuture.completedFuture(HttpResult.succeeded(null));
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

  //TODO: Need a better way of choosing behaviour for not found
  public CompletableFuture<HttpResult<User>> getUser(
    String userId,
    boolean failOnNotFound) {

    final Function<Response, HttpResult<User>> mapResponse = response -> {
      if(response.getStatusCode() == 404) {
        if(failOnNotFound) {
          return failed(new ValidationErrorFailure(
            new ValidationError("user is not found", "userId", userId)));
        }
        else {
          return HttpResult.succeeded(null);
        }
      }
      else if(response.getStatusCode() != 200) {
        return failed(new ForwardOnFailure(response));
      }
      else {
        //Got user record, we're good to continue
        return HttpResult.succeeded(new User(response.getJson()));
      }
    };

    return this.usersStorageClient.get(userId)
      .thenApply(mapResponse)
      .exceptionally(e -> failed(new ServerErrorFailure(e)));
  }
}
