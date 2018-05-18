package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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

  public static HttpResult<LoanAndRelatedRecords> refuseWhenUserIsNotAwaitingPickup(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    //TODO: Extract duplication between this and above
    return loanAndRelatedRecords.next(loan -> {
      final RequestQueue requestQueue = loan.requestQueue;
      final JsonObject requestingUser = loan.requestingUser;

      if(hasAwaitingPickupRequestForOtherPatron(requestQueue, requestingUser)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "User checking out must be requester awaiting pickup",
          "userBarcode", barcode));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenRequestingUserIsInactive(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    return loanAndRelatedRecords.next(loan -> {
      try {
        final User requestingUser = loan.requestingUser;

        if (!requestingUser.containsKey("active")) {
          return HttpResult.failure(new ValidationErrorFailure(
            "Cannot determine if user is active or not",
            "userBarcode", barcode));
        }
        if (requestingUser.isInactive()) {
          return HttpResult.failure(new ValidationErrorFailure(
            "Cannot check out to inactive user",
            "userBarcode", barcode));
        } else {
          return HttpResult.success(loan);
        }
      } catch (Exception e) {
        return HttpResult.failure(new ServerErrorFailure(e));
      }
    });
  }
  public static HttpResult<LoanAndRelatedRecords> refuseWhenProxyingUserIsInactive(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    return loanAndRelatedRecords.next(loan -> {
      final User proxyingUser = loan.proxyingUser;

      if(proxyingUser == null) {
        return loanAndRelatedRecords;
      }
      else if (!proxyingUser.containsKey("active")) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Cannot determine if proxying user is active or not",
          "proxyUserBarcode", barcode));
      }
      else if(proxyingUser.isInactive()) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Cannot check out via inactive proxying user",
          "proxyUserBarcode", barcode));
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

  public static CompletableFuture<HttpResult<LoanAndRelatedRecords>> refuseWhenHasOpenLoan(
    LoanAndRelatedRecords loanAndRelatedRecords,
    LoanRepository loanRepository,
    String barcode) {

    final String itemId = loanAndRelatedRecords.loan.getString("itemId");

    return loanRepository.hasOpenLoan(itemId)
      .thenApply(r -> r.next(openLoan -> {
        if(openLoan) {
          return HttpResult.failure(new ValidationErrorFailure(
            "Cannot check out item that already has an open loan", "itemBarcode", barcode));
        }
        else {
          return HttpResult.success(loanAndRelatedRecords);
        }
      }));
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      final JsonObject item = loan.inventoryRecords.item;

      if(ItemStatus.isCheckedOut(item)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item is already checked out", "itemId", item.getString("id")));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    //TODO: Extract duplication with above
    return loanAndRelatedRecords.next(loan -> {
      final JsonObject item = loan.inventoryRecords.item;

      if(ItemStatus.isCheckedOut(item)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item is already checked out", "itemBarcode", barcode));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }
}
