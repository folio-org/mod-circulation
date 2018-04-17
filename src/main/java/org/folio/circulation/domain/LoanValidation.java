package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;

public class LoanValidation {
  private LoanValidation() { }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if(loan.inventoryRecords.getItem() == null) {
        final String itemId = loan.loan.getString("itemId");

        return HttpResult.failure(new ValidationErrorFailure(
          "Item does not exist", "itemId", itemId));
      }
      else {
        return result;
      }
    });
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemBarcodeDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result, String barcode) {

    return result.next(loanAndRelatedRecords -> {
      if(loanAndRelatedRecords.inventoryRecords.getItem() == null) {
        return HttpResult.failure(new ValidationErrorFailure(
          String.format("No item with barcode %s exists", barcode),
          "itemBarcode", barcode));
      }
      else {
        return result;
      }
    });
  }

  public static void defaultStatusAndAction(JsonObject loan) {
    if(!loan.containsKey("status")) {
      loan.put("status", new JsonObject().put("name", "Open"));

      if(!loan.containsKey("action")) {
        loan.put("action", "checkedout");
      }
    }
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenUserIsNotAwaitingPickup(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      final RequestQueue requestQueue = loan.requestQueue;
      final JsonObject requestingUser = loan.requestingUser;

      if(hasAwaitingPickupRequestForOtherPatron(requestQueue, requestingUser)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "User checking out must be requester awaiting pickup",
          "userId", loan.loan.getString("userId")));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenRequestingUserIsInactive(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      try {
        final User requestingUser = loan.requestingUser;

        if (requestingUser.isInactive()) {
          return HttpResult.failure(new ValidationErrorFailure(
            "Cannot check out to inactive user",
            "userId", loan.loan.getString("userId")));
        } else {
          return HttpResult.success(loan);
        }
      } catch (Exception e) {
        return HttpResult.failure(new ServerErrorFailure(e));
      }
    });
  }
  public static HttpResult<LoanAndRelatedRecords> refuseWhenProxyingUserIsInactive(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      final User proxyingUser = loan.proxyingUser;

      if(proxyingUser == null) {
        return loanAndRelatedRecords;
      }
      else if(proxyingUser.isInactive()) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Cannot check out via inactive proxying user",
          "proxyUserId", loan.loan.getString("proxyUserId")));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  private static boolean hasAwaitingPickupRequestForOtherPatron(
    RequestQueue requestQueue,
    JsonObject requestingUser) {

    if(!requestQueue.hasOutstandingFulfillableRequests()) {
      return false;
    }
    else {
      final JsonObject highestPriority = requestQueue.getHighestPriorityFulfillableRequest();

      return isAwaitingPickup(highestPriority)
        && !isFor(highestPriority, requestingUser);
    }
  }

  private static boolean isFor(JsonObject request, JsonObject user) {
    return StringUtils.equals(request.getString("requesterId"), user.getString("id"));
  }

  private static boolean isAwaitingPickup(JsonObject highestPriority) {
    return StringUtils.equals(highestPriority.getString("status"), OPEN_AWAITING_PICKUP);
  }

  public static String determineLocationIdForItem(JsonObject item, JsonObject holding) {
    if(item != null && item.containsKey("temporaryLocationId")) {
      return item.getString("temporaryLocationId");
    }
    else if(holding != null && holding.containsKey("permanentLocationId")) {
      return holding.getString("permanentLocationId");
    }
    else if(item != null && item.containsKey("permanentLocationId")) {
      return item.getString("permanentLocationId");
    }
    else {
      return null;
    }
  }

  private static void handleProxy(CollectionResourceClient client,
                           String query, Consumer<Response> responseHandler){

    if(query != null){
      client.getMany(query, 1, 0, responseHandler);
    }
    else{
      responseHandler.accept(null);
    }
  }

  public static CompletableFuture<HttpResult<LoanAndRelatedRecords>> refuseWhenProxyRelationshipIsInvalid(
    LoanAndRelatedRecords loanAndRelatedRecords,
    Clients clients) {

    String validProxyQuery = CqlHelper.buildIsValidUserProxyQuery(
      loanAndRelatedRecords.loan.getString("proxyUserId"),
      loanAndRelatedRecords.loan.getString("userId"));

    if(validProxyQuery == null) {
      return CompletableFuture.completedFuture(HttpResult.success(loanAndRelatedRecords));
    }

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> future = new CompletableFuture<>();

    handleProxy(clients.userProxies(), validProxyQuery, proxyValidResponse -> {
      if (proxyValidResponse != null) {
        if (proxyValidResponse.getStatusCode() != 200) {
          future.complete(HttpResult.failure(new ForwardOnFailure(proxyValidResponse)));
          return;
        }

        final MultipleRecordsWrapper proxyRelationships = MultipleRecordsWrapper.fromBody(
          proxyValidResponse.getBody(), "proxiesFor");

        final Collection<JsonObject> unExpiredRelationships = proxyRelationships.getRecords()
          .stream()
          .filter(relationship -> {
            if(relationship.containsKey("meta")) {
              final JsonObject meta = relationship.getJsonObject("meta");

              if(meta.containsKey("expirationDate")) {
                final DateTime expirationDate = DateTime.parse(
                  meta.getString("expirationDate"));

                return expirationDate.isAfter(DateTime.now());
              }
              else {
                return true;
              }
            }
            else {
              return true;
            }
          })
          .collect(Collectors.toList());

        if (unExpiredRelationships.isEmpty()) { //if empty then we dont have a valid proxy id in the loan
          future.complete(HttpResult.failure(new ValidationErrorFailure(
            "proxyUserId is not valid", "proxyUserId",
            loanAndRelatedRecords.loan.getString("proxyUserId"))));
        }
        else {
          future.complete(HttpResult.success(loanAndRelatedRecords));
        }
      }
      else {
        future.complete(HttpResult.failure(
          new ServerErrorFailure("No response when requesting proxies")));
      }
    });

    return future;
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      final JsonObject item = loan.inventoryRecords.item;

      if(ItemStatus.isCheckedOut(item)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item is already checked out", "itemId", item.getString("id")));
      }
      else {
        return result;
      }
    });
  }
}
