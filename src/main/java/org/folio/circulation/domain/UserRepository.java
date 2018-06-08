package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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
      return CompletableFuture.completedFuture(HttpResult.success(null));
    }
    else {
      return getUserByBarcode(barcode, "proxyUserBarcode");
    }
  }

  public CompletableFuture<HttpResult<User>> getUserByBarcode(
    String barcode) {

    return getUserByBarcode(barcode, "userBarcode");
  }

  private CompletableFuture<HttpResult<User>> getUserByBarcode(
    String barcode,
    String propertyName) {

    final Function<Response, HttpResult<User>> mapResponse = response -> {
      if(response.getStatusCode() == 404) {
        return HttpResult.failure(new ServerErrorFailure("Unable to locate User"));
      }
      else if(response.getStatusCode() != 200) {
        return HttpResult.failure(new ForwardOnFailure(response));
      }
      else {
        //TODO: Check for multiple total records
        final MultipleRecordsWrapper wrappedUsers =
          MultipleRecordsWrapper.fromBody(response.getBody(), "users");

        final Optional<JsonObject> firstUser = wrappedUsers.getRecords().stream().findFirst();

        return firstUser.map(User::new).map(HttpResult::success).orElseGet(
          () -> HttpResult.failure(ValidationErrorFailure.failure(
          "Could not find user with matching barcode", propertyName, barcode)));
      }
    };

    return usersStorageClient.getMany(String.format("barcode==%s", barcode), 1, 0)
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  //TODO: Need a better way of choosing behaviour for not found
  public CompletableFuture<HttpResult<User>> getUser(
    String userId,
    boolean failOnNotFound) {

    final Function<Response, HttpResult<User>> mapResponse = response -> {
      if(response.getStatusCode() == 404) {
        if(failOnNotFound) {
          return HttpResult.failure(new ValidationErrorFailure(
            new ValidationError("user is not found", "userId", userId)));
        }
        else {
          return HttpResult.success(null);
        }
      }
      else if(response.getStatusCode() != 200) {
        return HttpResult.failure(new ForwardOnFailure(response));
      }
      else {
        //Got user record, we're good to continue
        return HttpResult.success(new User(response.getJson()));
      }
    };

    return this.usersStorageClient.get(userId)
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }
}
