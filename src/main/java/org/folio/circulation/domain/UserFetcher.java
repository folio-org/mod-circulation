package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class UserFetcher {

  private final CollectionResourceClient usersStorageClient;

  public UserFetcher(Clients clients) {
    usersStorageClient = clients.usersStorage();
  }

  public CompletableFuture<HttpResult<JsonObject>> getUser(String userId) {
    return getUser(userId, true);
  }

  public CompletableFuture<HttpResult<JsonObject>> getUserByBarcode(String barcode) {
    CompletableFuture<Response> getUserCompleted = new CompletableFuture<>();

    this.usersStorageClient.getMany(
      String.format("barcode==%s", barcode), 1, 0, getUserCompleted::complete);

    final Function<Response, HttpResult<JsonObject>> mapResponse = response -> {
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

        if(wrappedUsers.getRecords().isEmpty()) {
          return HttpResult.failure(new ValidationErrorFailure(
            "Could not find user with matching barcode", "userBarcode", barcode));
        }
        else {
          return HttpResult.success(wrappedUsers.getRecords().stream().findFirst().get());
        }
      }
    };

    return getUserCompleted
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  //TODO: Need a better way of choosing behaviour for not found
  public CompletableFuture<HttpResult<JsonObject>> getUser(
    String userId,
    boolean failOnNotFound) {

    CompletableFuture<Response> getUserCompleted = new CompletableFuture<>();

    this.usersStorageClient.get(userId, getUserCompleted::complete);

    final Function<Response, HttpResult<JsonObject>> mapResponse = response -> {
      if(response.getStatusCode() == 404) {
        if(failOnNotFound) {
          return HttpResult.failure(new ServerErrorFailure("Unable to locate User"));
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
        return HttpResult.success(response.getJson());
      }
    };

    return getUserCompleted
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }
}
